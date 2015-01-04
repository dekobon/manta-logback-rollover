import com.github.dekobon.MantaRolloverListener;
import com.github.dekobon.PutLogOnManata;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.assertTrue;

public class TestLogbackRolloverToManta {
    @Test
    public void rolloverTest() throws Exception {
        TestMantaRolloverListener listener = new TestMantaRolloverListener();
        PutLogOnManata.mantaRolloverListener = listener;

        Logger logger = LoggerFactory.getLogger("rollover.test");
        logger.info("Test message");

        Thread.sleep(10000);

        assertTrue("We expect that the listener is called after the log " +
                   "file has been successfully copied to Manta.",
                   listener.wasRolledOver);
    }

    class TestMantaRolloverListener implements MantaRolloverListener {
        boolean wasRolledOver = false;
        String logFilename;
        String mantaLogFilename;

        @Override
        public void rolledOver(String filename, String mantaFilename) {
            wasRolledOver = true;
            logFilename = filename;
            mantaLogFilename = mantaFilename;
        }
    }
}
