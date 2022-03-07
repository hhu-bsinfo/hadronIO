package de.hhu.bsinfo.hadronio.example.netty.benchmark.throughput;

import de.hhu.bsinfo.hadronio.util.ThroughputCombiner;
import de.hhu.bsinfo.hadronio.util.ThroughputResult;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;

import java.net.InetSocketAddress;
import java.util.concurrent.CyclicBarrier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Server implements Runnable {

    private static final Logger LOGGER = LoggerFactory.getLogger(Server.class);

    private final InetSocketAddress bindAddress;
    private final int messageSize;
    private final int messageCount;
    private final int aggregationThreshold;
    private final int connections;

    private int connectedChannels;
    private final SendRunnable[] runnables;
    private final Thread[] threads;

    private final Object connectionLock = new Object();
    private final Object connectionBarrier = new Object();
    private final CyclicBarrier benchmarkBarrier;

    public Server(final InetSocketAddress bindAddress, final int messageSize, final int messageCount, final int aggregationThreshold, final int connections) {
        this.bindAddress = bindAddress;
        this.messageSize = messageSize;
        this.messageCount = messageCount;
        this.aggregationThreshold = aggregationThreshold;
        this.connections = connections;
        benchmarkBarrier = new CyclicBarrier(connections);
        runnables = new SendRunnable[connections];
        threads = new Thread[connections];
    }

    @Override
    public void run() {
        LOGGER.info("Starting server on [{}]", bindAddress);
        final EventLoopGroup acceptorGroup = new NioEventLoopGroup();
        final EventLoopGroup workerGroup = new NioEventLoopGroup();
        final ServerBootstrap bootstrap = new ServerBootstrap();

        bootstrap.group(acceptorGroup, workerGroup)
            .channel(NioServerSocketChannel.class)
            .childHandler(
            new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel channel) {
                    final Object syncLock = new Object();
                    channel.closeFuture().addListener(future -> LOGGER.info("Closed channel connected to [{}]", channel.remoteAddress()));
                    channel.pipeline().addLast(new ServerHandler(syncLock));

                    synchronized (connectionLock) {
                        runnables[connectedChannels] = new SendRunnable(messageSize, messageCount, aggregationThreshold, syncLock, benchmarkBarrier, channel);
                        threads[connectedChannels] = new Thread(runnables[connectedChannels]);

                        if (++connectedChannels == connections) {
                            synchronized (connectionBarrier) {
                                connectionBarrier.notify();
                            }
                        }
                    }
                }
            });

        final Channel serverChannel = bootstrap.bind(bindAddress).addListener(future -> {
            if (future.isSuccess()) {
                LOGGER.info("Server is running");
            } else {
                LOGGER.error("Unable to start server", future.cause());
            }
        }).channel();
        serverChannel.closeFuture().addListener(future -> LOGGER.info("Server channel closed"));

        try {
            synchronized (connectionBarrier) {
                connectionBarrier.wait();
            }

            serverChannel.close();

            for (int i = 0; i < connections; i++) {
                threads[i].start();
            }

            final ThroughputCombiner combiner = new ThroughputCombiner();
            for (int i = 0; i < connections; i++) {
                threads[i].join();
                combiner.addResult(runnables[i].getResult());
            }

            final ThroughputResult result = combiner.getCombinedResult();
            LOGGER.info("{}", result);
        } catch (InterruptedException e) {
            LOGGER.error("A sync error occurred", e);
        } finally {
            acceptorGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
}
