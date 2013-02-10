package org.dancres.paxos.impl.netty;

import org.dancres.paxos.impl.Transport;
import org.dancres.paxos.impl.net.Utils;
import org.dancres.paxos.messages.Accept;
import org.dancres.paxos.messages.PaxosMessage;
import org.dancres.paxos.messages.codec.Codecs;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.*;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.channel.socket.DatagramChannel;
import org.jboss.netty.channel.socket.DatagramChannelFactory;
import org.jboss.netty.channel.socket.ServerSocketChannel;
import org.jboss.netty.channel.socket.SocketChannel;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioDatagramChannelFactory;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.channel.socket.oio.OioDatagramChannelFactory;
import org.jboss.netty.handler.codec.frame.FrameDecoder;
import org.jboss.netty.handler.codec.oneone.OneToOneDecoder;
import org.jboss.netty.handler.codec.oneone.OneToOneEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Paxos transport consists of two elements. The first is a shared multicast backplane that allows a node to efficiently
 * broadcast to all other nodes. This is typically used for failure detectors and leader to followers comms. The second
 * is a unicast connection used to talk to a specific node. This is typically used by followers wishing to respond
 * to a leader.
 *
 * The basic design assumptions for this transport are:
 *
 * <ol>
 * <li>Services that wish to base themselves upon Paxos consist of a client and a server component.</li>
 * <li>Transport between service server and client is independent of the Paxos transport.</li>
 * <li>Client sends a request to the server which is then sent to it's local leader.</li>
 * <li>Local leader will process the request and respond with success or failure.</li>
 * <li>Server acts upon the request and feeds back information to the client.</li>
 * <ul>
 * <li>If the local leader returns other_leader the server should probably send a message to the client which
 * redirects it to the server node housing the current leader. This can be effected by having each server
 * advertise itself with two addresses. The first is designed for use by clients, the second would be the Paxos
 * nodeId for that server. Other servers could thus translate from the Paxos nodeId for another leader to the
 * real service address.</li>
 * <li>If the local leader returns a vote timeout, the server may choose to retry the operation submit a number of
 * times and if that fails redirect the client or report a disconnection.</li>
 * <li>The server is responsible for maintaining a list of active client requests and dispatching back to them
 * based on the responses from it's local leader and acceptorlearner.</li>
 * </ul>
 * </ol>
 */
public class TransportImpl extends SimpleChannelHandler implements Transport {
	private static final Logger _logger = LoggerFactory
			.getLogger(TransportImpl.class);

	private static final int BROADCAST_PORT = 41952;

	private final InetSocketAddress _mcastAddr;
	private final DatagramChannelFactory _mcastFactory;
	private final DatagramChannel _mcast;
	private final DatagramChannelFactory _unicastFactory;
	private final DatagramChannel _unicast;
	private final NioServerSocketChannelFactory _serverStreamFactory;
	private final NioClientSocketChannelFactory _clientStreamFactory;
	private final Set<Dispatcher> _dispatcher = new HashSet<Dispatcher>();
	private final InetSocketAddress _unicastAddr;
    private final InetSocketAddress _broadcastAddr;
    private final AtomicBoolean _isStopping = new AtomicBoolean(false);
    private final PacketPickler _pickler = new PicklerImpl();
	
    private final ChannelGroup _channels = new DefaultChannelGroup();
    
    private class Factory implements ThreadFactory {
		public Thread newThread(Runnable aRunnable) {
			Thread myThread = new Thread(aRunnable);
			
			myThread.setDaemon(true);
			return myThread;			
		}
    }
    
    private class PicklerImpl implements PacketPickler {
        public byte[] pickle(Packet aPacket) {
			byte[] myBytes = Codecs.encode(aPacket.getMessage());
			ByteBuffer myBuffer = ByteBuffer.allocate(8 + 4 + myBytes.length);
			
			myBuffer.putLong(Codecs.flatten(aPacket.getSource()));
			myBuffer.putInt(myBytes.length);
			myBuffer.put(myBytes);
			myBuffer.flip();
			
			return myBuffer.array();
        }

        public Packet unpickle(byte[] aBytes) {
			ByteBuffer myBuffer = ByteBuffer.wrap(aBytes);
			
			InetSocketAddress mySource = Codecs.expand(myBuffer.getLong());
			int myLength = myBuffer.getInt();
			byte[] myPaxosBytes = new byte[myLength];
			myBuffer.get(myPaxosBytes);
			
			PaxosMessage myMessage = Codecs.decode(myPaxosBytes);
			
			return new PacketImpl(myMessage, mySource);        	
        }
    }

    static class PacketImpl implements Packet {
    	private final PaxosMessage _msg;
    	private final InetSocketAddress _source;
    	
    	PacketImpl(PaxosMessage aMsg, InetSocketAddress aSource) {
    		_msg = aMsg;
    		_source = aSource;
    	}
    	
		public InetSocketAddress getSource() {
    		return _source;
		}

		public PaxosMessage getMessage() {
			return _msg;
		}		
    }
    
    static {
    	System.runFinalizersOnExit(true);
    }
    
	public TransportImpl() throws Exception {
        _mcastAddr = new InetSocketAddress("224.0.0.1", BROADCAST_PORT);
        _broadcastAddr = new InetSocketAddress(Utils.getBroadcastAddress(), 255);

		InetSocketAddress myMcastTarget = new InetSocketAddress((InetAddress) null,
				BROADCAST_PORT);

		_mcastFactory = new OioDatagramChannelFactory(Executors.newCachedThreadPool(new Factory()));

		_mcast = _mcastFactory.newChannel(newPipeline());
		ChannelFuture myFuture = _mcast.bind(myMcastTarget);
		myFuture.await();
		_mcast.joinGroup(_mcastAddr.getAddress());
		_channels.add(_mcast);
		
		_unicastFactory = new NioDatagramChannelFactory(Executors.newCachedThreadPool(new Factory()));		
		_unicast = _unicastFactory.newChannel(newPipeline());
		myFuture = _unicast.bind(new InetSocketAddress(Utils.getWorkableInterface(), 0));
		myFuture.await();
		_channels.add(_unicast);
		
		_unicastAddr = _unicast.getLocalAddress();
		
		_logger.debug("Transport bound on: " + _unicastAddr);

		_serverStreamFactory = new NioServerSocketChannelFactory(Executors.newCachedThreadPool(new Factory()), 
				Executors.newCachedThreadPool());
        ServerSocketChannel myStreamChannel = _serverStreamFactory.newChannel(newPipeline());
		myStreamChannel.bind(_unicast.getLocalAddress());
		myStreamChannel.getConfig().setPipelineFactory(Channels.pipelineFactory(newPipeline()));
		_channels.add(myStreamChannel);
		
		_clientStreamFactory = new NioClientSocketChannelFactory(Executors.newCachedThreadPool(new Factory()),
				Executors.newCachedThreadPool());
    }

    public PacketPickler getPickler() {
    	return _pickler;
    }

	private void guard() {
		if (_isStopping.get())
			throw new IllegalStateException("Transport is stopped");
	}
	
    public void add(Dispatcher aDispatcher) throws Exception {
    	guard();
    	
        synchronized(this) {
            _dispatcher.add(aDispatcher);
            aDispatcher.setTransport(this);
        }
    }

    public void terminate() {
		_isStopping.set(true);
		
		try {
            synchronized(this) {
                for (Dispatcher d: _dispatcher)
                    try {
                        d.terminate();
                    } catch (Exception anE) {
                        _logger.warn("Dispatcher didn't terminate cleanly", anE);
                    }
            }

			_channels.close().await();
			
			_logger.debug("Stop mcast factory");
			_mcastFactory.releaseExternalResources();

			_logger.debug("Stop unicast factory");
			_unicastFactory.releaseExternalResources();

			_logger.debug("Stop serverstream factory");
			_serverStreamFactory.releaseExternalResources();
			
			_logger.debug("Stop clientstream factory");
			_clientStreamFactory.releaseExternalResources();			
			
			_logger.debug("Shutdown complete");
		} catch (Exception anE) {
			_logger.error("Failed to shutdown cleanly", anE);
		}

	}
	
	protected void finalize() throws Throwable {
		if (! _isStopping.get())
			_logger.warn("Failed to close transport before JVM exit");
	}
	
	private ChannelPipeline newPipeline() {
		ChannelPipeline myPipeline = Channels.pipeline();
		myPipeline.addLast("framer", new Framer());
		myPipeline.addLast("unframer", new Unframer());
		myPipeline.addLast("encoder", new Encoder());
		myPipeline.addLast("decoder", new Decoder());
		myPipeline.addLast("transport", this);
		
		return myPipeline;
	}
	
	public InetSocketAddress getLocalAddress() {
		return _unicastAddr;
	}

    public InetSocketAddress getBroadcastAddress() {
		guard();

		return _broadcastAddr;
    }
    
	public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
		_logger.debug("Connected: " + ctx + ", " + e);
		_channels.add(e.getChannel());
	}
	
	public void childChannelOpen(ChannelHandlerContext ctx, ChildChannelStateEvent e) throws Exception {
		_logger.debug("Stream open: " + ctx + ", " + e);
		_channels.add(e.getChannel());
	}
	
    public void messageReceived(ChannelHandlerContext aContext, MessageEvent anEvent) {
		if (_isStopping.get())
			return;

    	synchronized(this) {
            for(Dispatcher d : _dispatcher) {
                if (d.messageReceived((Packet) anEvent.getMessage()))
                    break;
            }
    	}
    }

    public void exceptionCaught(ChannelHandlerContext aContext, ExceptionEvent anEvent) {
        _logger.error("Problem in transport", anEvent.getCause());
        // anEvent.getChannel().close();
    }		
	
	public void send(PaxosMessage aMessage, InetSocketAddress aNodeId) {
		guard();
		
		try {
			if (aNodeId.equals(_broadcastAddr))
				_mcast.write(new PacketImpl(aMessage, getLocalAddress()), _mcastAddr);
			else {
				_unicast.write(new PacketImpl(aMessage, getLocalAddress()), aNodeId);
			}
		} catch (Exception anE) {
			_logger.error("Failed to write message", anE);
		}
	}

	private class StreamImpl implements Stream {
		private final SocketChannel _channel;
		
		StreamImpl(SocketChannel aChannel) {
			_channel = aChannel;
		}
		
		public void close() {
			try {
				_channels.remove(_channel);
				_channel.close().await();
			} catch (InterruptedException anIE) {
			}
		}
		
		public void send(PaxosMessage aMessage) {
            try {
                _channel.write(new PacketImpl(aMessage, getLocalAddress())).await();
            } catch (InterruptedException anIE) {
            }
		}

		public void sendRaw(Packet aPacket) {
			try {
				_channel.write(aPacket).await();
			} catch (InterruptedException anIE) {				
			}
		}
	}
	
	public void connectTo(final InetSocketAddress aNodeId, final ConnectionHandler aHandler) {
		guard();
		
		final SocketChannel myChannel = _clientStreamFactory.newChannel(newPipeline());

        myChannel.connect(aNodeId).addListener(new ChannelFutureListener() {
            public void operationComplete(ChannelFuture aFuture) throws Exception {
                if (aFuture.isSuccess()) {
                    _channels.add(myChannel);

                    aHandler.connected(new StreamImpl(myChannel));
                } else {
                    _logger.error("Couldn't connect to: " + aNodeId, aFuture.getCause());
                }
            }
        });
	}
	
	private class Encoder extends OneToOneEncoder {
		protected Object encode(ChannelHandlerContext aCtx, Channel aChannel, Object anObject) throws Exception {
			PacketImpl myPacket = (PacketImpl) anObject;
			
			return ByteBuffer.wrap(_pickler.pickle(myPacket));
		}
	}
	
	private class Decoder extends OneToOneDecoder {
		protected Object decode(ChannelHandlerContext aCtx, Channel aChannel, Object anObject) throws Exception {
			ByteBuffer myBuffer = (ByteBuffer) anObject;
			return _pickler.unpickle(myBuffer.array());
		}
	}
	
	private class Framer extends OneToOneEncoder {
		protected Object encode(ChannelHandlerContext aCtx, Channel aChannel, Object anObject) throws Exception {
			ChannelBuffer myBuff = ChannelBuffers.dynamicBuffer();
			ByteBuffer myMessage = (ByteBuffer) anObject;

			myBuff.writeInt(myMessage.limit());
			myBuff.writeBytes(myMessage.array());

			return myBuff;
		}
	}

	private class Unframer extends FrameDecoder {
		protected Object decode(ChannelHandlerContext aCtx, Channel aChannel, ChannelBuffer aBuffer) throws Exception {
			// Make sure if the length field was received.
			//
			if (aBuffer.readableBytes() < 4) {
				return null;
			}

			/*
			 * Mark the current buffer position before reading the length field
			 * because the whole frame might not be in the buffer yet. We will
			 * reset the buffer position to the marked position if there's not
			 * enough bytes in the buffer.
			 */
			aBuffer.markReaderIndex();

			// Read the length field.
			//
			int length = aBuffer.readInt();

			// Make sure if there's enough bytes in the buffer.
			//
			if (aBuffer.readableBytes() < length) {
				/*
				 * The whole bytes were not received yet - return null. This
				 * method will be invoked again when more packets are received
				 * and appended to the buffer.
				 * 
				 * Reset to the marked position to read the length field again
				 * next time.
				 */
				aBuffer.resetReaderIndex();

				return null;
			}

			// There's enough bytes in the buffer. Read it.
			ChannelBuffer frame = aBuffer.readBytes(length);

			return frame.toByteBuffer();
		}
	}
	
	public static void main(String[] anArgs) throws Exception {
		Transport _tport1 = new TransportImpl();
        _tport1.add(new DispatcherImpl());

		Transport _tport2 = new TransportImpl();
        _tport2.add(new DispatcherImpl());
		
		_tport1.send(new Accept(1, 2), _tport1.getBroadcastAddress());
		_tport1.send(new Accept(2, 3), _tport2.getLocalAddress());

		Thread.sleep(5000);
		_tport1.terminate();
		_tport2.terminate();
	}
	
	static class DispatcherImpl implements Dispatcher {
		public boolean messageReceived(Packet aPacket) {
			System.err.println("Message received: " + aPacket.getMessage());

            return true;
		}

		public void setTransport(Transport aTransport) {
			System.err.println("Dispatcher " + this + " got transport: " + aTransport);
		}

        public void terminate() {
            System.err.println("Dispatcher " + this + " got terminate");
        }
	}
}
