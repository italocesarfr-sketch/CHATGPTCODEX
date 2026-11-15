import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.*;

/** Existing key only. No key generation, serialization, passwords, alias or exception text in output. */
public final class SigningPreflight {
    private static String required(String key) {
        String value=System.getenv(key);
        if(value==null || value.isEmpty())throw new IllegalArgumentException();
        return value;
    }
    public static void main(String[] args) {
        String result="KEYSTORE_OR_CREDENTIALS";
        int status=0;
        char[] storePassword=null,keyPassword=null;
        try {
            String expected=required("NOCTUA_EXPECTED_CERT");
            if(!expected.matches("[0-9a-f]{64}"))throw new IllegalArgumentException();
            Path path=Path.of(required("DIARY_RELEASE_STORE_FILE"));
            storePassword=required("DIARY_RELEASE_STORE_PASSWORD").toCharArray();
            keyPassword=required("DIARY_RELEASE_KEY_PASSWORD").toCharArray();
            KeyStore store=KeyStore.getInstance("PKCS12");
            try(var input=Files.newInputStream(path)){store.load(input,storePassword);}
            String alias=required("DIARY_RELEASE_KEY_ALIAS");
            if(!store.isKeyEntry(alias))throw new IllegalArgumentException();
            X509Certificate cert=(X509Certificate)store.getCertificate(alias);
            cert.checkValidity();
            String actual=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(cert.getEncoded()));
            if(!actual.equals(expected)){result="CERTIFICATE_MISMATCH";throw new IllegalArgumentException();}
            Key key=store.getKey(alias,keyPassword);
            if(!(key instanceof PrivateKey))throw new IllegalArgumentException();
            String algorithm=switch(key.getAlgorithm()) {
                case "RSA" -> "SHA256withRSA";
                case "EC" -> "SHA256withECDSA";
                default -> throw new IllegalArgumentException();
            };
            Signature signature=Signature.getInstance(algorithm);
            byte[] challenge="NOcTuA existing key preflight, no APK signing".getBytes(StandardCharsets.UTF_8);
            signature.initSign((PrivateKey)key);signature.update(challenge);byte[] proof=signature.sign();
            signature.initVerify(cert);signature.update(challenge);
            if(!signature.verify(proof)){result="KEY_PROOF";throw new IllegalArgumentException();}
            Arrays.fill(proof,(byte)0);
            System.out.println("SIGNING_PREFLIGHT_OK");
        }catch(Exception e){
            System.out.println("SIGNING_PREFLIGHT_BLOCKED_"+result);
            status=1;
        }finally{
            if(storePassword!=null)Arrays.fill(storePassword,'\0');
            if(keyPassword!=null)Arrays.fill(keyPassword,'\0');
        }
        if(status!=0)System.exit(status);
    }
}
