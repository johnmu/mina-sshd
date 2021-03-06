/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.sshd.common.signature;

import java.security.KeyPair;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.Factory;
import org.apache.sshd.common.NamedFactory;
import org.apache.sshd.common.RuntimeSshException;
import org.apache.sshd.common.cipher.ECCurves;
import org.apache.sshd.common.config.keys.DSSPublicKeyEntryDecoder;
import org.apache.sshd.common.config.keys.ECDSAPublicKeyEntryDecoder;
import org.apache.sshd.common.config.keys.PublicKeyEntryDecoder;
import org.apache.sshd.common.config.keys.RSAPublicKeyDecoder;
import org.apache.sshd.common.keyprovider.AbstractKeyPairProvider;
import org.apache.sshd.common.keyprovider.KeyPairProvider;
import org.apache.sshd.common.util.SecurityUtils;
import org.apache.sshd.common.util.ValidateUtils;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.util.test.BaseTestSupport;
import org.apache.sshd.util.test.Utils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * @author <a href="mailto:dev@mina.apache.org">Apache MINA SSHD Project</a>
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@RunWith(Parameterized.class)   // see https://github.com/junit-team/junit/wiki/Parameterized-tests
public class SignatureFactoriesTest extends BaseTestSupport {
    private static SshServer sshd;
    private static SshClient client;
    private static int port;

    private final String keyType;
    private final int keySize;
    private final NamedFactory<Signature> factory;
    private final PublicKeyEntryDecoder<?, ?> pubKeyDecoder;

    public SignatureFactoriesTest(String keyType, NamedFactory<Signature> factory, int keySize, PublicKeyEntryDecoder<?, ?> decoder) {
        this.keyType = ValidateUtils.checkNotNullAndNotEmpty(keyType, "No key type specified");
        this.factory = Objects.requireNonNull(factory, "No signature factory provided");
        ValidateUtils.checkTrue(keySize > 0, "Invalid key size: %d", keySize);
        this.keySize = keySize;
        this.pubKeyDecoder = Objects.requireNonNull(decoder, "No public key decoder provided");
    }

    @Parameters(name = "type={0}, size={2}")
    public static List<Object[]> parameters() {
        List<Object[]> list = new ArrayList<>();
        addTests(list, KeyPairProvider.SSH_DSS, BuiltinSignatures.dsa, DSS_SIZES, DSSPublicKeyEntryDecoder.INSTANCE);
        addTests(list, KeyPairProvider.SSH_RSA, BuiltinSignatures.rsa, RSA_SIZES, RSAPublicKeyDecoder.INSTANCE);
        if (SecurityUtils.hasEcc()) {
            for (ECCurves curve : ECCurves.VALUES) {
                BuiltinSignatures factory = BuiltinSignatures.fromFactoryName(curve.getKeyType());
                addTests(list, curve.getName(), factory, Collections.singletonList(curve.getKeySize()), ECDSAPublicKeyEntryDecoder.INSTANCE);
            }
        }
        return Collections.unmodifiableList(list);
    }

    private static void addTests(List<Object[]> list, String keyType, NamedFactory<Signature> factory, Collection<Integer> sizes, PublicKeyEntryDecoder<?, ?> decoder) {
        for (Integer keySize : sizes) {
            list.add(new Object[]{keyType, factory, keySize, decoder});
        }
    }

    @BeforeClass
    public static void setupClientAndServer() throws Exception {
        sshd = Utils.setupTestServer(SignatureFactoriesTest.class);
        sshd.start();
        port = sshd.getPort();

        client = Utils.setupTestClient(SignatureFactoriesTest.class);
        client.start();
    }

    @AfterClass
    public static void tearDownClientAndServer() throws Exception {
        if (sshd != null) {
            try {
                sshd.stop(true);
            } finally {
                sshd = null;
            }
        }

        if (client != null) {
            try {
                client.stop();
            } finally {
                client = null;
            }
        }
    }

    public final int getKeySize() {
        return keySize;
    }

    public final String getKeyType() {
        return keyType;
    }

    @Test
    public void testPublicKeyAuth() throws Exception {
        testKeyPairProvider(getKeyType(), getKeySize(), pubKeyDecoder, Collections.singletonList(factory));
    }

    protected void testKeyPairProvider(
            final String keyName, final int keySize, final PublicKeyEntryDecoder<?, ?> decoder, List<NamedFactory<Signature>> signatures)
                    throws Exception {
        testKeyPairProvider(keyName, () -> {
            try {
                KeyPair kp = decoder.generateKeyPair(keySize);
                outputDebugMessage("Generated key pair for %s - key size=%d", keyName, keySize);
                return Collections.singletonList(kp);
            } catch (Exception e) {
                throw new RuntimeSshException(e);
            }
        }, signatures);
    }

    protected void testKeyPairProvider(
            final String keyName, final Factory<Iterable<KeyPair>> keyPairFactory, List<NamedFactory<Signature>> signatures)
                    throws Exception {
        final Iterable<KeyPair> iter = keyPairFactory.create();
        testKeyPairProvider(new AbstractKeyPairProvider() {
            @Override
            public Iterable<KeyPair> loadKeys() {
                return iter;
            }
        }, signatures);
    }

    protected void testKeyPairProvider(KeyPairProvider provider, List<NamedFactory<Signature>> signatures) throws Exception {
        sshd.setKeyPairProvider(provider);
        client.setSignatureFactories(signatures);
        try (ClientSession s = client.connect(getCurrentTestName(), TEST_LOCALHOST, port).verify(7L, TimeUnit.SECONDS).getSession()) {
            s.addPasswordIdentity(getCurrentTestName());
            // allow a rather long timeout since generating some keys may take some time
            s.auth().verify(30L, TimeUnit.SECONDS);
        }
    }
}
