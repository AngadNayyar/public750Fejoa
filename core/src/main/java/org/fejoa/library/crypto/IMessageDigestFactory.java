/*
 * Copyright 2014-2017.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.crypto;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;


public interface IMessageDigestFactory {
    MessageDigest create() throws NoSuchAlgorithmException;
}
