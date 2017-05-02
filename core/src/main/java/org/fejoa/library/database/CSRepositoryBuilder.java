/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.database;

import org.fejoa.library.FejoaContext;
import org.fejoa.library.SymmetricKeyData;
import org.fejoa.library.crypto.CryptoException;
import org.fejoa.library.crypto.ICryptoInterface;
import org.apache.commons.codec.binary.Base64;
import org.fejoa.chunkstore.*;

import java.io.*;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;


public class CSRepositoryBuilder {
    static public Repository openOrCreate(final FejoaContext context, File dir, String branch, HashValue commit,
                                          SymmetricKeyData keyData) throws IOException, CryptoException {
        ChunkStore chunkStore;
        if (ChunkStore.exists(dir, branch))
            chunkStore = ChunkStore.open(dir, branch);
        else
            chunkStore = ChunkStore.create(dir, branch);
        IRepoChunkAccessors accessors = getRepoChunkAccessors(context, chunkStore, keyData);
        ICommitCallback commitCallback = getCommitCallback(context, keyData);

        return new Repository(dir, branch, commit, accessors, commitCallback);
    }

    static public Repository openOrCreate(final FejoaContext context, File dir, String branch, SymmetricKeyData keyData)
            throws IOException, CryptoException {
        return openOrCreate(context, dir, branch, null, keyData);
    }

    static public ICommitCallback getCommitCallback(final FejoaContext context,
                                                     SymmetricKeyData keyData) {
        if (keyData == null)
            return getSimpleCommitCallback();
        if (keyData instanceof SymmetricKeyData)
            return getEncCommitCallback(context, keyData);
        throw new RuntimeException("Don't know how to create the commit callback.");
    }

    final static private int DATA_TAG = 0;
    final static private int BOX_TAG = 1;

    private static byte[] commitPointerToLog(ChunkContainerRef commitPointer) throws IOException {
        ProtocolBufferLight buffer = new ProtocolBufferLight();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        commitPointer.getData().write(outputStream);
        buffer.put(DATA_TAG, outputStream.toByteArray());

        outputStream = new ByteArrayOutputStream();
        commitPointer.getBox().write(outputStream);
        buffer.put(BOX_TAG, outputStream.toByteArray());

        return buffer.toByteArray();
    }

    private static ChunkContainerRef commitPointerFromLog(byte[] bytes) throws IOException {
        ProtocolBufferLight buffer = new ProtocolBufferLight(bytes);
        ChunkContainerRef ref = new ChunkContainerRef();
        byte[] dataBytes = buffer.getBytes(DATA_TAG);
        if (dataBytes == null)
            throw new IOException("Missing data part");
        ref.getData().read(new ByteArrayInputStream(dataBytes));

        byte[] boxBytes = buffer.getBytes(BOX_TAG);
        if (boxBytes == null)
            throw new IOException("Missing data part");
        ref.getBox().read(new ByteArrayInputStream(boxBytes));
        return ref;
    }

    static final int TAG_IV = 0;
    static final int TAG_ENCDATA = 1;

    private static ICommitCallback getEncCommitCallback(final FejoaContext context,
                                                        final SymmetricKeyData keyData) {
        return new ICommitCallback() {
            byte[] encrypt(byte[] plain, byte[] iv) throws CryptoException {
                ICryptoInterface cryptoInterface = context.getCrypto();
                return cryptoInterface.encryptSymmetric(plain, keyData.key, iv, keyData.settings);
            }

            byte[] decrypt(byte[] cipher, byte[] iv) throws CryptoException {
                ICryptoInterface cryptoInterface = context.getCrypto();
                return cryptoInterface.decryptSymmetric(cipher, keyData.key, iv, keyData.settings);
            }

            @Override
            public HashValue logHash(ChunkContainerRef commitPointer) {
                try {
                    MessageDigest digest = commitPointer.getDataMessageDigest();
                    digest.update(commitPointer.getBox().getBoxHash().getBytes());
                    digest.update(commitPointer.getBox().getIV());
                    return new HashValue(digest.digest());
                } catch (IOException e) {
                    throw new RuntimeException("Missing hash algorithm");
                }
            }

            @Override
            public String commitPointerToLog(ChunkContainerRef commitPointer) throws CryptoException {
                ProtocolBufferLight protoBuffer = new ProtocolBufferLight();
                try {
                    byte[] buffer = CSRepositoryBuilder.commitPointerToLog(commitPointer);

                    byte[] iv = context.getCrypto().generateInitializationVector(keyData.settings.ivSize);
                    byte[] encryptedMessage = encrypt(buffer, iv);
                    protoBuffer.put(TAG_IV, iv);
                    protoBuffer.put(TAG_ENCDATA, encryptedMessage);
                    return Base64.encodeBase64String(protoBuffer.toByteArray());
                } catch (IOException e) {
                    e.printStackTrace();
                    throw new RuntimeException("Should not happen (?)");
                }
            }

            @Override
            public ChunkContainerRef commitPointerFromLog(String logEntry) throws CryptoException {
                try {
                    ProtocolBufferLight protoBuffer = new ProtocolBufferLight(Base64.decodeBase64(logEntry));
                    byte[] iv = protoBuffer.getBytes(TAG_IV);
                    byte[] plain = decrypt(protoBuffer.getBytes(TAG_ENCDATA), iv);
                    return CSRepositoryBuilder.commitPointerFromLog(plain);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return null;
            }
        };
    }

    public static ICommitCallback getSimpleCommitCallback() {
        return new ICommitCallback() {
            @Override
            public HashValue logHash(ChunkContainerRef commitPointer) {
                return commitPointer.getBox().getBoxHash();
            }

            @Override
            public String commitPointerToLog(ChunkContainerRef commitPointer) {
                try {
                    byte[] buffer = CSRepositoryBuilder.commitPointerToLog(commitPointer);
                    return Base64.encodeBase64String(buffer);
                } catch (IOException e) {
                    e.printStackTrace();
                    assert false;
                }
                return null;
            }

            @Override
            public ChunkContainerRef commitPointerFromLog(String logEntry) {
                byte[] logEntryBytes = Base64.decodeBase64(logEntry);
                try {
                    return CSRepositoryBuilder.commitPointerFromLog(logEntryBytes);
                } catch (IOException e) {
                    e.printStackTrace();
                    assert false;
                }
                return null;
            }
        };
    }

    static private IRepoChunkAccessors getRepoChunkAccessors(final FejoaContext context, final ChunkStore chunkStore,
                                                             SymmetricKeyData keyData) throws IOException {
        if (keyData == null)
            return getPlainRepoChunkAccessors(chunkStore);

        return getEncryptionAccessors(context, chunkStore, keyData);
    }

    static private IChunkAccessor getEncryptionChunkAccessor(final FejoaContext context,
                                                             final ChunkStore.Transaction transaction,
                                                             final SymmetricKeyData keyData,
                                                             final ChunkContainerRef ref) {
        return new IChunkAccessor() {
            final ICryptoInterface cryptoInterface = context.getCrypto();

            private byte[] getIv(byte[] hashValue) {
                final int ivSizeBytes = keyData.settings.ivSize / 8;
                byte[] iv = Arrays.copyOfRange(hashValue, 0, ivSizeBytes);
                // xor with the base IV
                for (int i = 0; i < ivSizeBytes; i++)
                    iv[i] = (byte)(keyData.iv[i] ^ iv[i]);
                return iv;
            }

            @Override
            public DataInputStream getChunk(ChunkPointer hash) throws IOException, CryptoException {
                byte[] iv = getIv(hash.getIV());
                byte[] chunkData = transaction.getChunk(hash.getBoxHash());
                if (chunkData == null)
                    throw new IOException("Chunk not found: " + hash.getBoxHash());
                InputStream inputStream = new ByteArrayInputStream(chunkData);
                inputStream = cryptoInterface.decryptSymmetric(inputStream, keyData.key, iv, keyData.settings);
                if (ref.getBoxHeader().getCompressionType() == BoxHeader.CompressionType.ZLIB_COMPRESSION)
                    inputStream = new InflaterInputStream(inputStream);
                return new DataInputStream(inputStream);
            }

            @Override
            public PutResult<HashValue> putChunk(byte[] data, HashValue ivHash) throws IOException, CryptoException {
                ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
                OutputStream outputStream = byteOutputStream;
                outputStream = cryptoInterface.encryptSymmetric(outputStream, keyData.key,
                        getIv(ivHash.getBytes()), keyData.settings);
                if (ref.getBoxHeader().getCompressionType() == BoxHeader.CompressionType.ZLIB_COMPRESSION)
                    outputStream = new DeflaterOutputStream(outputStream);
                outputStream.write(data);
                outputStream.close();
                return transaction.put(byteOutputStream.toByteArray());
            }

            @Override
            public void releaseChunk(HashValue data) {

            }
        };
    }

    static private IRepoChunkAccessors getEncryptionAccessors(final FejoaContext context, final ChunkStore chunkStore,
                                                              final SymmetricKeyData keyData) {
        return new IRepoChunkAccessors() {
            @Override
            public ITransaction startTransaction() throws IOException {
                return new RepoAccessorsTransactionBase(chunkStore) {
                    @Override
                    public ChunkStore.Transaction getRawAccessor() {
                        return transaction;
                    }

                    @Override
                    public IChunkAccessor getCommitAccessor(ChunkContainerRef ref) {
                        return getEncryptionChunkAccessor(context, transaction, keyData, ref);
                    }

                    @Override
                    public IChunkAccessor getTreeAccessor(ChunkContainerRef ref) {
                        return getEncryptionChunkAccessor(context, transaction, keyData, ref);
                    }

                    @Override
                    public IChunkAccessor getFileAccessor(ChunkContainerRef ref, String filePath) {
                        return getEncryptionChunkAccessor(context, transaction, keyData, ref);
                    }
                };
            }
        };
    }

    static private IRepoChunkAccessors getPlainRepoChunkAccessors(final ChunkStore chunkStore) {
        return new IRepoChunkAccessors() {
            @Override
            public ITransaction startTransaction() throws IOException {
                return new RepoAccessorsTransactionBase(chunkStore) {
                    final IChunkAccessor accessor = new IChunkAccessor() {
                        @Override
                        public DataInputStream getChunk(ChunkPointer hash) throws IOException {
                            byte[] chunkData = transaction.getChunk(hash.getBoxHash());
                            if (chunkData == null)
                                throw new IOException("Chunk not found: " + hash.getBoxHash());
                            return new DataInputStream(new ByteArrayInputStream(chunkData));
                        }

                        @Override
                        public PutResult<HashValue> putChunk(byte[] data, HashValue ivHash) throws IOException {
                            return transaction.put(data);
                        }

                        @Override
                        public void releaseChunk(HashValue data) {

                        }
                    };

                    @Override
                    public ChunkStore.Transaction getRawAccessor() {
                        return transaction;
                    }

                    @Override
                    public IChunkAccessor getCommitAccessor(ChunkContainerRef ref) {
                        return accessor;
                    }

                    @Override
                    public IChunkAccessor getTreeAccessor(ChunkContainerRef ref) {
                        return accessor;
                    }

                    @Override
                    public IChunkAccessor getFileAccessor(ChunkContainerRef ref, String filePath) {
                        return accessor;
                    }
                };
            }
        };
    }
}
