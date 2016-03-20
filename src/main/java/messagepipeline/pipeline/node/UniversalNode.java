package messagepipeline.pipeline.node;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CyclicBarrier;

public abstract class UniversalNode {

    public final InetSocketAddress address;
    protected final CyclicBarrier batchStart;
    protected final CyclicBarrier batchEnd;
    protected final Path baseDir;
    protected final String name;
    public volatile Path path;
    protected volatile boolean process = true;

    public UniversalNode(String name, String directory, InetSocketAddress address, CyclicBarrier start, CyclicBarrier end) {
        this.name = name;
        this.baseDir = Paths.get(directory);
        this.address = address;
        this.batchStart = start;
        this.batchEnd = end;
        this.process = true;
    }

    public abstract void signalStepEnd();
    private static final Logger logger = LoggerFactory.getLogger(UniversalNode.class);
    public void finish() {
        process = false;
        logger.debug("finish");
    }

    public final String getName() {
        return name;
    }

    public final void addStep(String p) {
        if( p != null &&  !"".equals(p)) {
            path = Paths.get(this.baseDir + File.separator + p);
        } else {
            path = null;
        }
    }
}
