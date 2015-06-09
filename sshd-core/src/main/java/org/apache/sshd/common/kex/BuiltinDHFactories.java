/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.sshd.common.kex;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;

import org.apache.sshd.common.NamedFactory;
import org.apache.sshd.common.NamedResource;
import org.apache.sshd.common.cipher.ECCurves;
import org.apache.sshd.common.config.NamedResourceListParseResult;
import org.apache.sshd.common.digest.BuiltinDigests;
import org.apache.sshd.common.util.GenericUtils;
import org.apache.sshd.common.util.SecurityUtils;
import org.apache.sshd.common.util.ValidateUtils;

/**
 * @author <a href="mailto:dev@mina.apache.org">Apache MINA SSHD Project</a>
 */
public enum BuiltinDHFactories implements DHFactory {
    dhg1(Constants.DIFFIE_HELLMAN_GROUP1_SHA1) {
        @Override
        public DHG create(Object... params) throws Exception {
            if (!GenericUtils.isEmpty(params)) {
                throw new IllegalArgumentException("No accepted parameters for " + getName());
            }
            return new DHG(BuiltinDigests.sha1, new BigInteger(DHGroupData.getP1()), new BigInteger(DHGroupData.getG()));
        }
    },
    dhg14(Constants.DIFFIE_HELLMAN_GROUP14_SHA1) {
        @Override
        public DHG create(Object... params) throws Exception {
            if (!GenericUtils.isEmpty(params)) {
                throw new IllegalArgumentException("No accepted parameters for " + getName());
            }
            return new DHG(BuiltinDigests.sha1, new BigInteger(DHGroupData.getP14()), new BigInteger(DHGroupData.getG()));
        }

        @Override
        public boolean isSupported() {
            return SecurityUtils.isBouncyCastleRegistered();
        }
    },
    dhgex(Constants.DIFFIE_HELLMAN_GROUP_EXCHANGE_SHA1) {
        @Override
        public DHG create(Object... params) throws Exception {
            if ((GenericUtils.length(params) != 2)
             || (!(params[0] instanceof BigInteger))
             || (!(params[1] instanceof BigInteger))) {
                throw new IllegalArgumentException("Bad parameters for " + getName());
            }
            return new DHG(BuiltinDigests.sha1, (BigInteger) params[0], (BigInteger) params[1]);
        }

        @Override
        public boolean isGroupExchange() {
            return true;
        }
    },
    dhgex256(Constants.DIFFIE_HELLMAN_GROUP_EXCHANGE_SHA256) {
        @Override
        public AbstractDH create(Object... params) throws Exception {
            if ((GenericUtils.length(params) != 2)
             || (!(params[0] instanceof BigInteger))
             || (!(params[1] instanceof BigInteger))) {
                throw new IllegalArgumentException("Bad parameters for " + getName());
            }
            return new DHG(BuiltinDigests.sha256, (BigInteger) params[0], (BigInteger) params[1]);
        }

        @Override
        public boolean isSupported() {  // avoid "Prime size must be multiple of 64, and can only range from 512 to 2048 (inclusive)"
            return SecurityUtils.isBouncyCastleRegistered();
        }

        @Override
        public boolean isGroupExchange() {
            return true;
        }
    },
    ecdhp256(Constants.ECDH_SHA2_NISTP256) {
        @Override
        public ECDH create(Object... params) throws Exception {
            if (!GenericUtils.isEmpty(params)) {
                throw new IllegalArgumentException("No accepted parameters for " + getName());
            }
            return new ECDH(ECCurves.NISTP256);
        }

        @Override
        public boolean isSupported() {
            return SecurityUtils.hasEcc();
        }
    },
    ecdhp384(Constants.ECDH_SHA2_NISTP384) {
        @Override
        public ECDH create(Object... params) throws Exception {
            if (!GenericUtils.isEmpty(params)) {
                throw new IllegalArgumentException("No accepted parameters for " + getName());
            }
            return new ECDH(ECCurves.NISTP384);
        }

        @Override
        public boolean isSupported() {
            return SecurityUtils.hasEcc();
        }
    },
    ecdhp521(Constants.ECDH_SHA2_NISTP521) {
        @Override
        public ECDH create(Object... params) throws Exception {
            if (!GenericUtils.isEmpty(params)) {
                throw new IllegalArgumentException("No accepted parameters for " + getName());
            }
            return new ECDH(ECCurves.NISTP521);
        }

        @Override
        public boolean isSupported() {
            return SecurityUtils.hasEcc();
        }
    };

    private final String factoryName;

    @Override
    public final String getName() {
        return factoryName;
    }

    @Override
    public boolean isSupported() {
        return true;
    }

    @Override
    public final String toString() {
        return getName();
    }

    BuiltinDHFactories(String name) {
        factoryName = name;
    }

    public static final Set<BuiltinDHFactories> VALUES =
            Collections.unmodifiableSet(EnumSet.allOf(BuiltinDHFactories.class));
    private static final Map<String,DHFactory>   extensions = 
            new TreeMap<String,DHFactory>(String.CASE_INSENSITIVE_ORDER);

    /**
     * Registered a {@link NamedFactory} to be available besides the built-in
     * ones when parsing configuration
     * @param extension The factory to register
     * @throws IllegalArgumentException if factory instance is {@code null},
     * or overrides a built-in one or overrides another registered factory
     * with the same name (case <U>insensitive</U>).
     */
    public static final void registerExtension(DHFactory extension) {
        String  name=ValidateUtils.checkNotNull(extension, "No extension provided", GenericUtils.EMPTY_OBJECT_ARRAY).getName();
        ValidateUtils.checkTrue(fromFactoryName(name) == null, "Extension overrides built-in: %s", name);

        synchronized(extensions) {
            ValidateUtils.checkTrue(!extensions.containsKey(name), "Extension overrides existinh: %s", name);
            extensions.put(name, extension);
        }
    }

    /**
     * @return A {@link SortedSet} of the currently registered extensions, sorted
     * according to the factory name (case <U>insensitive</U>)
     */
    public static final SortedSet<DHFactory> getRegisteredExtensions() {
        // TODO for JDK-8 return Collections.emptySortedSet()
        synchronized(extensions) {
            return GenericUtils.asSortedSet(NamedResource.BY_NAME_COMPARATOR, extensions.values());
        }
    }

    /**
     * Unregisters specified extension
     * @param name The factory name - ignored if {@code null}/empty
     * @return The registered extension - {@code null} if not found
     */
    public static final DHFactory unregisterExtension(String name) {
        if (GenericUtils.isEmpty(name)) {
            return null;
        }
        
        synchronized(extensions) {
            return extensions.remove(name);
        }
    }

    /**
     * @param name The factory name - ignored if {@code null}/empty
     * @return The matching {@link BuiltinDHFactories} (case <U>insensitive</U>)
     * or {@code null} if no match found
     */
    public static final BuiltinDHFactories fromFactoryName(String name) {
        if (GenericUtils.isEmpty(name)) {
            return null;
        }

        for (BuiltinDHFactories f : VALUES) {
            if (name.equalsIgnoreCase(f.getName())) {
                return f;
            }
        }

        return null;
    }

    @Override
    public boolean isGroupExchange() {
        return false;
    }

    /**
     * @param dhList A comma-separated list of ciphers' names - ignored
     * if {@code null}/empty
     * @return A {@link ParseResult} of all the {@link DHFactory}-ies whose
     * name appears in the string and represent a built-in value. Any
     * unknown name is <U>ignored</U>. The order of the returned result
     * is the same as the original order - bar the unknown ones.
     * <B>Note:</B> it is up to caller to ensure that the list does not
     * contain duplicates
     */
    public static final ParseResult parseDHFactoriesList(String dhList) {
        return parseDHFactoriesList(GenericUtils.split(dhList, ','));
    }

    public static final ParseResult parseDHFactoriesList(String ... dhList) {
        return parseDHFactoriesList(GenericUtils.isEmpty((Object[]) dhList) ? Collections.<String>emptyList() : Arrays.asList(dhList));
    }

    public static final ParseResult parseDHFactoriesList(Collection<String> dhList) {
        if (GenericUtils.isEmpty(dhList)) {
            return ParseResult.EMPTY;
        }
        
        List<DHFactory> factories=new ArrayList<DHFactory>(dhList.size());
        List<String>    unknown=Collections.<String>emptyList();
        for (String name : dhList) {
            DHFactory  f=resolveFactory(name);
            if (f != null) {
                factories.add(f);
            } else {
                // replace the (unmodifiable) empty list with a real one
                if (unknown.isEmpty()) {
                    unknown = new ArrayList<String>();
                }
                unknown.add(name);
            }
        }
        
        return new ParseResult(factories, unknown);
    }
    /**
     * @param name The factory name
     * @return The factory or {@code null} if it is neither a built-in one
     * or a registered extension 
     */
    public static final DHFactory resolveFactory(String name) {
        if (GenericUtils.isEmpty(name)) {
            return null;
        }

        DHFactory  s=fromFactoryName(name);
        if (s != null) {
            return s;
        }
        
        synchronized(extensions) {
            return extensions.get(name);
        }
    }

    /**
     * Represents the result of {@link BuiltinDHFactories#parseDHFactoriesList(String)}
     * @author <a href="mailto:dev@mina.apache.org">Apache MINA SSHD Project</a>
     */
    public static final class ParseResult extends NamedResourceListParseResult<DHFactory> {
        public static final ParseResult EMPTY=new ParseResult(Collections.<DHFactory>emptyList(), Collections.<String>emptyList());
        
        public ParseResult(List<DHFactory> parsed, List<String> unsupported) {
            super(parsed, unsupported);
        }
        
        public List<DHFactory> getParsedFactories() {
            return getParsedResources();
        }
        
        public List<String> getUnsupportedFactories() {
            return getUnsupportedResources();
        }
    }

    public static final class Constants {
        public static final String DIFFIE_HELLMAN_GROUP1_SHA1 = "diffie-hellman-group1-sha1";
        public static final String DIFFIE_HELLMAN_GROUP14_SHA1 = "diffie-hellman-group14-sha1";
        public static final String DIFFIE_HELLMAN_GROUP_EXCHANGE_SHA1 = "diffie-hellman-group-exchange-sha1";
        public static final String DIFFIE_HELLMAN_GROUP_EXCHANGE_SHA256 = "diffie-hellman-group-exchange-sha256";
        public static final String ECDH_SHA2_NISTP256 = "ecdh-sha2-nistp256";
        public static final String ECDH_SHA2_NISTP384 = "ecdh-sha2-nistp384";
        public static final String ECDH_SHA2_NISTP521 = "ecdh-sha2-nistp521";
    }
}