/*
 * Copyright (C) 2024 crDroid Android Project
 *               2024 the risingOS Android Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.internal.util.android;

import android.app.ActivityThread;
import android.content.Context;
import android.os.SystemProperties;
import android.security.keystore.KeyProperties;
import android.system.keystore2.KeyEntryResponse;
import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.concurrent.ThreadLocalRandom;

import com.android.internal.org.bouncycastle.asn1.ASN1Boolean;
import com.android.internal.org.bouncycastle.asn1.ASN1Encodable;
import com.android.internal.org.bouncycastle.asn1.ASN1EncodableVector;
import com.android.internal.org.bouncycastle.asn1.ASN1Enumerated;
import com.android.internal.org.bouncycastle.asn1.ASN1ObjectIdentifier;
import com.android.internal.org.bouncycastle.asn1.ASN1Sequence;
import com.android.internal.org.bouncycastle.asn1.ASN1TaggedObject;
import com.android.internal.org.bouncycastle.asn1.DEROctetString;
import com.android.internal.org.bouncycastle.asn1.DERSequence;
import com.android.internal.org.bouncycastle.asn1.DERTaggedObject;
import com.android.internal.org.bouncycastle.asn1.x509.Extension;
import com.android.internal.org.bouncycastle.cert.X509CertificateHolder;
import com.android.internal.org.bouncycastle.cert.X509v3CertificateBuilder;
import com.android.internal.org.bouncycastle.operator.ContentSigner;
import com.android.internal.org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import com.android.internal.R;

/**
 * @hide
 */
public final class KeyEntryHooks {
    private static final String TAG = KeyEntryHooks.class.getSimpleName();
    public static final String ENTRY_HOOKS_ENABLED_PROP = "persist.sys.entryhooks_enabled";
    private static final String FALLBACK_EC_PRIVATE_KEY = "MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQgZD40XzfCEMydUW9mpLuTkl5QZV2tPxbmak0Z2eOMMXmhRANCAAQpUJNXlGs+lkFDtO1hhZYfpnjIdkdhQLu4AvdBHhsA2RUtFJGXwgwdp+3B31unHwFtiNnTq180CAo69/tcb32o";
    private static PrivateKey EC, RSA;
    private static byte[] EC_CERTS, RSA_CERTS;
    private static final ASN1ObjectIdentifier OID = new ASN1ObjectIdentifier("1.3.6.1.4.1.11129.2.1.17");
    private static CertificateFactory certificateFactory;
    private static X509CertificateHolder EC_holder, RSA_holder;
    private static volatile String algo;
    
    static {
        if (SystemProperties.getBoolean(ENTRY_HOOKS_ENABLED_PROP, false)) {
            try {
                Context context = ActivityThread.currentApplication().getApplicationContext();
                if (context != null) {
                    certificateFactory = CertificateFactory.getInstance("X.509");
                    String[] ecPrivateKeys = context.getResources().getStringArray(R.array.config_key_ec_private);
                    if (ecPrivateKeys != null && ecPrivateKeys.length > 0) {
                        ecPrivateKeys = sanitizeString(ecPrivateKeys);
                        try {
                            EC = parsePrivateKey(ecPrivateKeys[0], KeyProperties.KEY_ALGORITHM_EC);
                        } catch (Throwable t) {}
                    }
                    String[] rsaPrivateKeys = context.getResources().getStringArray(R.array.config_key_rsa_private);
                    if (rsaPrivateKeys != null && rsaPrivateKeys.length > 0) {
                        rsaPrivateKeys = sanitizeString(rsaPrivateKeys);
                        try {
                            RSA = parsePrivateKey(rsaPrivateKeys[0], KeyProperties.KEY_ALGORITHM_RSA);
                        } catch (Throwable t) {}
                    }
                    String[] ecCertificates = context.getResources().getStringArray(R.array.config_cert_ec);
                    if (ecCertificates != null && ecCertificates.length > 0) {
                        ecCertificates = sanitizeString(ecCertificates);
                        EC_CERTS = loadCertificates(ecCertificates);
                        EC_holder = new X509CertificateHolder(parseCert(ecCertificates[0]));
                    }
                    String[] rsaCertificates = context.getResources().getStringArray(R.array.config_cert_rsa);
                    if (rsaCertificates != null && rsaCertificates.length > 0) {
                        rsaCertificates = sanitizeString(rsaCertificates);
                        RSA_CERTS = loadCertificates(rsaCertificates);
                        RSA_holder = new X509CertificateHolder(parseCert(rsaCertificates[0]));
                    }
                }
            } catch (Exception e) {}
        }
    }

    private static String[] sanitizeString(String[] string) {
        if (string == null) return null;
        String[] outputString = new String[string.length];
        for (int i = 0; i < string.length; i++) {
            outputString[i] = string[i] != null ? string[i].replaceAll("[\\s\\n]+", "") : null;
        }
        return outputString;
    }

    private static PrivateKey parsePrivateKey(String str, String algo) throws Throwable {
        if (str == null || str.isEmpty()) {
            throw new IllegalArgumentException("Private keys cannot be null or empty");
        }
        byte[] bytes = Base64.getDecoder().decode(str);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(bytes);
        try {
            return KeyFactory.getInstance(algo).generatePrivate(spec);
        } catch (Exception e) {
            // some keys in prime256v1 ec curve throws InvalidKeySpecException
            // when that happens, return a fallback key and let the devs know about the error
            Log.e(TAG, "Failed to parse EC private key, returning fallback key: ", e);
            return KeyFactory.getInstance(algo)
                    .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(FALLBACK_EC_PRIVATE_KEY)));
        }
    }

    private static byte[] loadCertificates(String[] certs) throws Exception {
        if (certs == null || certs.length == 0) {
            throw new IllegalArgumentException("Certificates cannot be null or empty");
        }
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        for (String cert : certs) {
            if (cert != null && !cert.isEmpty()) {
                stream.write(parseCert(cert));
            }
        }
        return stream.toByteArray();
    }

    private static byte[] parseCert(String str) {
        if (str == null || str.isEmpty()) {
            throw new IllegalArgumentException("Certificate cannot be null or empty");
        }
        return Base64.getDecoder().decode(str);
    }

    private static byte[] getCertificateChain(String algo) throws Throwable {
        if (KeyProperties.KEY_ALGORITHM_EC.equals(algo)) {
            return EC_CERTS;
        } else if (KeyProperties.KEY_ALGORITHM_RSA.equals(algo)) {
            return RSA_CERTS;
        }
        throw new Exception("Unknown algorithm: " + algo);
    }

    private static byte[] modifyLeaf(byte[] bytes) throws Throwable {
        X509Certificate leaf = (X509Certificate) certificateFactory.generateCertificate(new ByteArrayInputStream(bytes));
        if (leaf.getExtensionValue(OID.getId()) == null) throw new Exception("Missing extension");
        X509CertificateHolder holder = new X509CertificateHolder(leaf.getEncoded());
        Extension ext = holder.getExtension(OID);
        ASN1Sequence sequence = ASN1Sequence.getInstance(ext.getExtnValue().getOctets());
        ASN1Encodable[] encodables = sequence.toArray();
        ASN1Sequence teeEnforced = (ASN1Sequence) encodables[7];
        ASN1EncodableVector vector = new ASN1EncodableVector();
        ASN1Sequence rootOfTrust = null;
        for (ASN1Encodable asn1Encodable : teeEnforced) {
            ASN1TaggedObject taggedObject = (ASN1TaggedObject) asn1Encodable;
            if (taggedObject.getTagNo() == 704) {
                rootOfTrust = (ASN1Sequence) taggedObject.getObject();
                continue;
            }
            vector.add(asn1Encodable);
        }
        if (rootOfTrust == null) throw new Exception("Missing root of trust");
        algo = leaf.getPublicKey().getAlgorithm();
        boolean isEC = KeyProperties.KEY_ALGORITHM_EC.equals(algo);
        X509CertificateHolder cert1 = isEC ? EC_holder : RSA_holder;
        PrivateKey privateKey = isEC ? EC : RSA;
        X509v3CertificateBuilder builder = new X509v3CertificateBuilder(cert1.getSubject(),
                holder.getSerialNumber(), holder.getNotBefore(), holder.getNotAfter(),
                holder.getSubject(), holder.getSubjectPublicKeyInfo());
        ContentSigner signer = new JcaContentSignerBuilder(leaf.getSigAlgName()).build(privateKey);
        byte[] verifiedBootKey = new byte[32];
        ThreadLocalRandom.current().nextBytes(verifiedBootKey);
        DEROctetString verifiedBootHash = (DEROctetString) rootOfTrust.getObjectAt(3);
        if (verifiedBootHash == null) {
            byte[] temp = new byte[32];
            ThreadLocalRandom.current().nextBytes(temp);
            verifiedBootHash = new DEROctetString(temp);
        }
        ASN1Encodable[] rootOfTrustEnc = {
                new DEROctetString(verifiedBootKey),
                ASN1Boolean.TRUE,
                new ASN1Enumerated(0),
                new DEROctetString(verifiedBootHash)
        };
        ASN1Sequence rootOfTrustSeq = new DERSequence(rootOfTrustEnc);
        ASN1TaggedObject rootOfTrustTagObj = new DERTaggedObject(704, rootOfTrustSeq);
        vector.add(rootOfTrustTagObj);
        vector.add(ASN1Boolean.TRUE);
        return builder.build(signer).getEncoded();
    }

    public static KeyEntryResponse onGetKeyEntry(KeyEntryResponse response) {
        if (response == null)
            return null;
        if (!SystemProperties.getBoolean(ENTRY_HOOKS_ENABLED_PROP, false))
            return response;
        if (response.metadata == null)
            return response;
        algo = null;
        try {
            byte[] newLeaf = modifyLeaf(response.metadata.certificate);
            response.metadata.certificateChain = getCertificateChain(algo);
            response.metadata.certificate = newLeaf;
        } catch (Throwable t) {}
        return response;
    }
}
