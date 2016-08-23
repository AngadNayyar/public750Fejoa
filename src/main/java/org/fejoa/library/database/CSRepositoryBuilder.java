/*
 * Copyright 2016.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.database;

import org.apache.commons.codec.binary.Base64;
import org.fejoa.chunkstore.*;
import org.fejoa.library.*;
import org.fejoa.library.crypto.*;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.util.Arrays;


public class CSRepositoryBuilder {

    static public Repository openOrCreate(final FejoaContext context, File dir, String branch, SymmetricKeyData keyData)
            throws IOException, CryptoException {
        ChunkStore chunkStore = ChunkStore.create(dir, branch);
        IRepoChunkAccessors accessors = getRepoChunkAccessors(context, chunkStore, keyData);
        Repository.ICommitCallback commitCallback = getCommitCallback(context, keyData);

        return new Repository(dir, branch, accessors, commitCallback);
    }

    private static Repository.ICommitCallback getCommitCallback(final FejoaContext context,
                                                                SymmetricKeyData keyData) {
        if (keyData == null)
            return getSimpleCommitCallback();
        if (keyData instanceof SymmetricKeyData)
            return getEncCommitCallback(context, keyData);
        throw new RuntimeException("Don't know how to create the commit callback.");
    }

    static final String DATA_HASH_KEY = "dataHash";
    static final String BOX_HASH_KEY = "boxHash";
    static final String BOX_IV_KEY = "boxIV";

    private static Repository.ICommitCallback getEncCommitCallback(final FejoaContext context,
                                                                   final SymmetricKeyData keyData) {
        return new Repository.ICommitCallback() {
            byte[] encrypt(byte[] plain, byte[] iv) throws CryptoException {
                ICryptoInterface cryptoInterface = context.getCrypto();
                return cryptoInterface.encryptSymmetric(plain, keyData.key, iv, keyData.settings);
            }

            byte[] decrypt(byte[] cipher, byte[] iv) throws CryptoException {
                ICryptoInterface cryptoInterface = context.getCrypto();
                return cryptoInterface.decryptSymmetric(cipher, keyData.key, iv, keyData.settings);
            }

            @Override
            public String commitPointerToLog(BoxPointer commitPointer) throws CryptoException {
                JSONObject jsonObject = new JSONObject();
                try {
                    jsonObject.put(DATA_HASH_KEY, commitPointer.getDataHash().toHex());
                    jsonObject.put(BOX_HASH_KEY, commitPointer.getBoxHash().toHex());
                    jsonObject.put(BOX_IV_KEY, Base64.encodeBase64String(commitPointer.getIV()));
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                byte[] iv = context.getCrypto().generateInitializationVector(keyData.settings.ivSize);
                String encryptedMessage = Base64.encodeBase64String(encrypt(jsonObject.toString().getBytes(), iv));

                JSONObject out = new JSONObject();
                try {
                    out.put(Constants.IV_KEY, Base64.encodeBase64String(iv));
                    out.put(Constants.MESSAGE_KEY, encryptedMessage);
                } catch (JSONException e) {
                    e.printStackTrace();
                    throw new RuntimeException("Should not happen (?)");
                }
                return Base64.encodeBase64String(out.toString().getBytes());
            }

            @Override
            public BoxPointer commitPointerFromLog(String logEntry) throws CryptoException {
                String jsonString = new String(Base64.decodeBase64(logEntry));
                try {
                    JSONObject in = new JSONObject(jsonString);
                    byte[] iv = Base64.decodeBase64(in.getString(Constants.IV_KEY));
                    byte[] plain = decrypt(Base64.decodeBase64(in.getString(Constants.MESSAGE_KEY)), iv);
                    JSONObject jsonObject = new JSONObject(new String(plain));
                    return new BoxPointer(HashValue.fromHex(jsonObject.getString(DATA_HASH_KEY)),
                            HashValue.fromHex(jsonObject.getString(BOX_HASH_KEY)),
                            Base64.decodeBase64(jsonObject.getString(BOX_IV_KEY)));
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                return null;
            }
        };
    }

    private static Repository.ICommitCallback getSimpleCommitCallback() {
        return new Repository.ICommitCallback() {
            @Override
            public String commitPointerToLog(BoxPointer commitPointer) {
                JSONObject jsonObject = new JSONObject();
                try {
                    jsonObject.put(DATA_HASH_KEY, commitPointer.getDataHash().toHex());
                    jsonObject.put(BOX_HASH_KEY, commitPointer.getBoxHash().toHex());
                    jsonObject.put(BOX_IV_KEY, Base64.encodeBase64String(commitPointer.getIV()));
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                String jsonString = jsonObject.toString();
                return Base64.encodeBase64String(jsonString.getBytes());
            }

            @Override
            public BoxPointer commitPointerFromLog(String logEntry) {
                String jsonString = new String(Base64.decodeBase64(logEntry));
                try {
                    JSONObject jsonObject = new JSONObject(jsonString);
                    return new BoxPointer(HashValue.fromHex(jsonObject.getString(DATA_HASH_KEY)),
                            HashValue.fromHex(jsonObject.getString(BOX_HASH_KEY)),
                            Base64.decodeBase64(jsonObject.getString(BOX_IV_KEY)));
                } catch (JSONException e) {
                    e.printStackTrace();
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
                                                             final SymmetricKeyData keyData) {
        return new IChunkAccessor() {
            final ICryptoInterface cryptoInterface = context.getCrypto();

            private byte[] getIv(byte[] hashValue) {
                final int ivSize = keyData.settings.ivSize;
                byte[] iv = Arrays.copyOfRange(hashValue, 0, ivSize);
                // xor with the base IV
                for (int i = 0; i < ivSize; i++)
                    iv[i] = (byte)(keyData.iv[i] ^ iv[i]);
                return iv;
            }

            @Override
            public DataInputStream getChunk(BoxPointer hash) throws IOException, CryptoException {
                byte[] iv = getIv(hash.getIV());
                return new DataInputStream(cryptoInterface.decryptSymmetric(new ByteArrayInputStream(
                                transaction.getChunk(hash.getBoxHash())),
                        keyData.key, iv, keyData.settings));
            }

            @Override
            public PutResult<HashValue> putChunk(byte[] data, HashValue ivHash) throws IOException, CryptoException {
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                OutputStream cryptoStream = cryptoInterface.encryptSymmetric(outputStream, keyData.key,
                        getIv(ivHash.getBytes()), keyData.settings);
                cryptoStream.write(data);
                return transaction.put(outputStream.toByteArray());
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
                    final IChunkAccessor accessor = getEncryptionChunkAccessor(context, transaction, keyData);

                    @Override
                    public ChunkStore.Transaction getRawAccessor() {
                        return transaction;
                    }

                    @Override
                    public IChunkAccessor getCommitAccessor() {
                        return accessor;
                    }

                    @Override
                    public IChunkAccessor getTreeAccessor() {
                        return accessor;
                    }

                    @Override
                    public IChunkAccessor getFileAccessor(String filePath) {
                        return accessor;
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
                        public DataInputStream getChunk(BoxPointer hash) throws IOException {
                            return new DataInputStream(new ByteArrayInputStream(transaction.getChunk(hash.getBoxHash())));
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
                    public IChunkAccessor getCommitAccessor() {
                        return accessor;
                    }

                    @Override
                    public IChunkAccessor getTreeAccessor() {
                        return accessor;
                    }

                    @Override
                    public IChunkAccessor getFileAccessor(String filePath) {
                        return accessor;
                    }
                };
            }
        };
    }
}
