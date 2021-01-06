package de.hhu.bsinfo.ucx;

import de.hhu.bsinfo.ucx.generated.BuildConfig;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.ProtocolFamily;
import java.nio.channels.DatagramChannel;
import java.nio.channels.Pipe;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.AbstractSelector;
import java.nio.channels.spi.SelectorProvider;
import java.util.stream.Collectors;

public class UcxProvider extends SelectorProvider {

    protected static final int DEFAULT_SERVER_PORT = 2998;

    @Override
    public DatagramChannel openDatagramChannel() throws IOException {
        throw new UnsupportedOperationException("Operation not supported!");
    }

    @Override
    public DatagramChannel openDatagramChannel(ProtocolFamily protocolFamily) throws IOException {
        throw new UnsupportedOperationException("Operation not supported!");
    }

    @Override
    public Pipe openPipe() throws IOException {
        throw new UnsupportedOperationException("Operation not supported!");
    }

    @Override
    public AbstractSelector openSelector() throws IOException {
        return new UcxSelector(this);
    }

    @Override
    public ServerSocketChannel openServerSocketChannel() throws IOException {
        return new UcxServerSocketChannel(this);
    }

    @Override
    public SocketChannel openSocketChannel() throws IOException {
        return new UcxSocketChannel(this);
    }

    public static void printBanner() {
        InputStream inputStream = UcxProvider.class.getClassLoader().getResourceAsStream("banner.txt");

        if (inputStream == null) {
            return;
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        String banner = reader.lines().collect(Collectors.joining(System.lineSeparator()));

        System.out.print("\n");
        System.out.printf(banner, BuildConfig.VERSION, BuildConfig.BUILD_DATE, BuildConfig.GIT_BRANCH, BuildConfig.GIT_COMMIT);
        System.out.print("\n\n");
    }
}
