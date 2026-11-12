import java.nio.file.*;
import java.security.*;
import java.util.Base64;
public class CatalogSigner {
 public static void main(String[] args)throws Exception{
  KeyStore keys=KeyStore.getInstance("PKCS12");try(var in=Files.newInputStream(Path.of(args[0]))){keys.load(in,"android".toCharArray());}
  PrivateKey key=(PrivateKey)keys.getKey("androiddebugkey","android".toCharArray());
  Signature signer=Signature.getInstance("SHA256withRSA");signer.initSign(key);signer.update("NOVA-HOLODECK-CATALOG-V1\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));signer.update(Files.readAllBytes(Path.of(args[1])));
  Files.writeString(Path.of(args[2]),Base64.getEncoder().encodeToString(signer.sign()));
 }
}
