/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.chunkstore;

import junit.framework.TestCase;
import org.fejoa.library.crypto.*;
import org.fejoa.library.support.StorageLib;

import javax.crypto.SecretKey;
import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;


public class BenchmarkTest extends TestCase {
    final List<String> cleanUpFiles = new ArrayList<String>();
    CryptoSettings settings = CryptoSettings.getDefault();
    ICryptoInterface cryptoInterface = new BCCryptoInterface();
    SecretKey secretKey;
    final ChunkSplitter splitter = new RabinSplitter();

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        secretKey = cryptoInterface.generateSymmetricKey(settings.symmetric);
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();

        for (String dir : cleanUpFiles)
            StorageLib.recursiveDeleteFile(new File(dir));
    }

    private IChunkAccessor getSimpleAccessor(final ChunkStore chunkStore) throws IOException {
        return new IChunkAccessor() {
            ChunkStore.Transaction transaction = chunkStore.openTransaction();

            @Override
            public DataInputStream getChunk(ChunkPointer hash) throws IOException {
                return new DataInputStream(new ByteArrayInputStream(chunkStore.getChunk(hash.getBoxHash().getBytes())));
            }

            @Override
            public PutResult<HashValue> putChunk(byte[] data, HashValue ivHash) throws IOException {
                return transaction.put(data);
            }

            @Override
            public void releaseChunk(HashValue data) {

            }
        };
    }

    private IChunkAccessor getEncAccessor(final ChunkStore chunkStore) throws CryptoException, IOException {
        return new IChunkAccessor() {
            ChunkStore.Transaction transaction = chunkStore.openTransaction();

            private byte[] getIv(byte[] hashValue) {
                return Arrays.copyOfRange(hashValue, 0, settings.symmetric.ivSize / 8);
            }

            @Override
            public DataInputStream getChunk(ChunkPointer hash) throws IOException, CryptoException {
                byte[] iv = getIv(hash.getDataHash().getBytes());
                return new DataInputStream(cryptoInterface.decryptSymmetric(new ByteArrayInputStream(
                                chunkStore.getChunk(hash.getBoxHash().getBytes())),
                        secretKey, iv, settings.symmetric));
            }

            @Override
            public PutResult<HashValue> putChunk(byte[] data, HashValue ivHash) throws IOException, CryptoException {
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                OutputStream cryptoStream = cryptoInterface.encryptSymmetric(outputStream, secretKey,
                        getIv(ivHash.getBytes()), settings.symmetric);
                cryptoStream.write(data);
                return transaction.put(outputStream.toByteArray());
            }

            @Override
            public void releaseChunk(HashValue data) {

            }
        };
    }

    private IChunkAccessor getAccessor(ChunkStore chunkStore) throws IOException, CryptoException {
        //return getEncAccessor(chunkStore);
        return getSimpleAccessor(chunkStore);
    }

    private ChunkContainer prepareContainer(String dirName, String name, ChunkContainerRef ref)
            throws IOException,
            CryptoException {
        cleanUpFiles.add(dirName);
        File dir = new File(dirName);
        dir.mkdirs();

        final ChunkStore chunkStore = ChunkStore.create(dir, name);

        IChunkAccessor accessor = getAccessor(chunkStore);
        ChunkContainer chunkContainer = new ChunkContainer(accessor, ref);

        return chunkContainer;
    }

    private ChunkContainer openContainer(String dirName, String name, ChunkContainerRef ref)
            throws IOException,
            CryptoException {
        final ChunkStore chunkStore = ChunkStore.open(new File(dirName), name);
        IChunkAccessor accessor = getAccessor(chunkStore);
        ChunkContainer chunkContainer = ChunkContainer.read(accessor, ref);
        return chunkContainer;
    }

    static private void fillRandom(byte[] buffer, long seed) {
        Random random = new Random();
        random.setSeed(seed);
        for (int i = 0; i < buffer.length; i++)
            buffer[i] = (byte) (256 * random.nextFloat());
    }

    static class WriteJob {
        final int orgDataSize;
        final int writesPerJob;
        final int overwriteSize;
        final List<WriteData> writeDataList = new ArrayList<>();

        public WriteJob(int dataLength, int writesPerJob, int overwriteSize, long seed) {
            this.orgDataSize = dataLength;
            this.writesPerJob = writesPerJob;
            this.overwriteSize = overwriteSize;
            for (int i = 0; i < writesPerJob; i++)
                writeDataList.add(new WriteData(dataLength, overwriteSize, seed++));
        }

        static String headerString(String prefix) {
            return prefix + "OrgDataSize, " + prefix + "WritesPerJob, " + prefix + "OverwriteSize";
        }

        @Override
        public String toString() {
            return "" + orgDataSize + ", " + writesPerJob + ", " + overwriteSize;
        }
    }

    static class WriteData {
        final long writePosition;
        final byte[] data;
        final long seed;

        WriteData(int orgDataSize, int writeLength, long seed) {
            this.seed = seed;
            Random random = new Random();
            random.setSeed(seed);

            this.writePosition = (long)(orgDataSize * random.nextFloat());
            this.data = new byte[writeLength];
            fillRandom(data, seed + 1);
        }

        @Override
        public String toString() {
            return "Pos: " + writePosition + " Len: " + data.length + " Seed: " + seed;
        }
    }

    static class Result {
        final WriteJob job;
        final long time;

        Result(WriteJob job, long time) {
            this.job = job;
            this.time = time;
        }

        static String headerString(String prefix) {
            return WriteJob.headerString(prefix) + ", " + prefix + "Time";
        }

        @Override
        public String toString() {
            return "" + job.toString() + ", " + time;
        }
    }

    static class TestRun {
        final List<Result> fileResults = new ArrayList<>();
        final List<Result> chunkContainerResults = new ArrayList<>();

        static String headerString() {
            return Result.headerString("File") + ", " + Result.headerString("ChunkContainer");
        }

        @Override
        public String toString() {
            String string = "";
            for (int i = 0; i < fileResults.size(); i++) {
                string += fileResults.get(i).toString();
                string += ",";
                string += chunkContainerResults.get(i).toString();
                if (i < fileResults.size() - 1)
                    string += "\n";
            }
            return string;
        }
    }

    private List<Result> preformWriteTestFile(byte[] data, List<WriteJob> jobList)
            throws IOException, NoSuchAlgorithmException {
        String fileName = "file.tmp";
        cleanUpFiles.add(fileName);
        File file = new File(fileName);
        if (file.exists())
            file.delete();
        file.createNewFile();
        FileOutputStream outputStream = new FileOutputStream(file);
        outputStream.write(data);
        outputStream.flush();
        outputStream.close();

        List<Result> results = new ArrayList<>();
        MessageDigest messageDigest = CryptoHelper.sha256Hash();
        for (WriteJob job : jobList) {
            messageDigest.reset();
            RandomAccessFile randomAccessFile = new RandomAccessFile(file, "rw");
            long startTime = System.currentTimeMillis();
            for (WriteData writeData : job.writeDataList) {
                randomAccessFile.seek(writeData.writePosition);
                randomAccessFile.write(writeData.data);
            }
            randomAccessFile.close();
            for (byte b : data)
                messageDigest.update(b);
            messageDigest.digest();
            results.add(new Result(job, System.currentTimeMillis() - startTime));
        }
        return results;
    }

    private List<Result> preformWriteTestChunkContainer(byte[] data, List<WriteJob> jobList)
            throws IOException, NoSuchAlgorithmException, CryptoException {
        final String dirName = "testWriteBenchmark";
        final String name = "test";
        int minSize = 1024 * 2;
        int maxSize = 1024 * 512;
        ChunkContainerRef ref = new ChunkContainerRef();
        ref.getContainerHeader().setRabinChunking(minSize, maxSize);
        ChunkContainer chunkContainer = prepareContainer(dirName, name, ref);
        ChunkContainerOutputStream outputStream = new ChunkContainerOutputStream(chunkContainer,
                chunkContainer.getChunkSplitter());
        // hack to avoid out of memory:
        final int copySize = 1024 * 1024 * 128;
        long startTime = System.currentTimeMillis();
        System.out.println("Start filling chunk container");
        for (int i = 0; i < data.length; i+= copySize) {
            int toWrite = Math.min(copySize, data.length - i);
            outputStream.write(data, i, toWrite);
            // reopen to free memory
            /*outputStream.close();
            chunkContainer.flush(false);
            ChunkPointer pointer = chunkContainer.getChunkPointer();
            chunkContainer = openContainer(dirName, name, pointer, nodeSplitter);
            outputStream = new ChunkContainerOutputStream(chunkContainer, dataSplitter);*/
            System.out.println("Fill chunk container progress: " + ((i + toWrite) / (1024 * 1024)));
        }
        outputStream.close();
        chunkContainer.flush(false);
        System.out.println("Time to fill the chunk container: " + (System.currentTimeMillis() - startTime));
        assert chunkContainer.getDataLength() == data.length;
        ChunkContainerRef pointer = chunkContainer.getRef();

        List<Result> results = new ArrayList<>();
        for (WriteJob job : jobList) {
            chunkContainer = openContainer(dirName, name, pointer);
            outputStream = new ChunkContainerOutputStream(chunkContainer, chunkContainer.getChunkSplitter());
            System.out.println("Start write job:");
            for (WriteData writeData : job.writeDataList) {
                System.out.println("Write data: " + writeData.toString());
            }
            startTime = System.currentTimeMillis();
            for (WriteData writeData : job.writeDataList) {
                //System.out.println("Write data: " + writeData.toString());
                outputStream.seek(writeData.writePosition);
                outputStream.write(writeData.data);
            }
            outputStream.close();
            chunkContainer.flush(false);
            chunkContainer.hash();
            results.add(new Result(job, System.currentTimeMillis() - startTime));
        }
        return results;
    }

    private TestRun doTestRun(byte[] data, List<WriteJob> jobList) throws IOException, NoSuchAlgorithmException, CryptoException {
        TestRun testRun = new TestRun();
        testRun.fileResults.addAll(preformWriteTestFile(data, jobList));
        testRun.chunkContainerResults.addAll(preformWriteTestChunkContainer(data, jobList));
        return testRun;
    }

    private List<WriteJob> prepareWriteJobs(int dataLength, int[] writesPerJobArray, int[] overwriteSizeArray,
                                            long seed) {
        List<WriteJob> jobList = new ArrayList<>();
        for (int writesPerJob : writesPerJobArray) {
            for (int overwriteSize : overwriteSizeArray)
                jobList.add(new WriteJob(dataLength, writesPerJob, overwriteSize, seed++));
        }
        return jobList;
    }

    public void testWrite() throws IOException, CryptoException, NoSuchAlgorithmException {

        int[] dataSizes = {
                1024 * 1024,
                1024 * 1024 * 2,
                /*1024 * 1024 * 4,
                1024 * 1024 * 8,
                1024 * 1024 * 16,
                1024 * 1024 * 32,
                1024 * 1024 * 64,
                1024 * 1024 * 128,
                1024 * 1024 * 256,*/
                //1024 * 1024 * 512,
                //1024 * 1024 * 1024
        };

        int[] overwriteSizes = {
                4 * 1024,
                128 * 1024,
        };

        int[] writesPerJob = {
                1,
                //2,
                4,
                //8,
                //16
        };

        int nIterations = 10;
        List<TestRun> results = new ArrayList<>();
        for (Integer nBytes : dataSizes) {
            System.out.println("Data size: " + nBytes);
            byte[] data = new byte[nBytes];
            long seedData = (long) (9999999l * Math.random());
            //long seedData = 2463267L;
            System.out.println("Data seed: " + seedData);
            fillRandom(data, seedData);

            for (int i = 0; i < nIterations; i++) {
                System.out.println("Iteration: " + i);

                long seedJob = (long) (9999999l * Math.random());
                //long seedJob = 1585788L;
                System.out.println("Job seed: " + seedJob);
                List<WriteJob> writeJobList = prepareWriteJobs(data.length, writesPerJob, overwriteSizes, seedJob);
                TestRun run = doTestRun(data, writeJobList);
                results.add(run);
            }
        }

        System.out.println(TestRun.headerString());
        for (TestRun run : results)
            System.out.println(run);
    }
}
