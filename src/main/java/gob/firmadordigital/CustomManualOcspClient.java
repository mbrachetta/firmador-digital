package gob.firmadordigital;

import com.itextpdf.signatures.IOcspClient;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.ocsp.OCSPResponseStatus;
import org.bouncycastle.asn1.x509.AccessDescription;
import org.bouncycastle.asn1.x509.AuthorityInformationAccess;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;
import org.bouncycastle.cert.ocsp.BasicOCSPResp;
import org.bouncycastle.cert.ocsp.CertificateID;
import org.bouncycastle.cert.ocsp.OCSPReq;
import org.bouncycastle.cert.ocsp.OCSPReqBuilder;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.bouncycastle.operator.DigestCalculatorProvider;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

public class CustomManualOcspClient implements IOcspClient {

    public static byte[] getOcspResponseBytes(
            X509Certificate checkCert,
            X509Certificate issuerCert
    ) {
        return new CustomManualOcspClient().getEncoded(checkCert, issuerCert, null);
    }

    @Override
    public byte[] getEncoded(
            X509Certificate checkCert,
            X509Certificate issuerCert,
            String url
    ) {
        if (checkCert == null || issuerCert == null) {
            return null;
        }

        try {
            List<String> ocspUrls = new ArrayList<>();

            if (url != null && !url.isBlank()) {
                ocspUrls.add(url);
            } else {
                ocspUrls.addAll(getOcspUrls(checkCert));
            }

            if (ocspUrls.isEmpty()) {
                return null;
            }

            byte[] requestBytes = buildOcspRequestBytes(checkCert, issuerCert);

            for (String ocspUrl : ocspUrls) {
                byte[] ocspHttpResponse = postOcspRequest(ocspUrl, requestBytes);

                if (ocspHttpResponse == null || ocspHttpResponse.length == 0) {
                    continue;
                }

                byte[] basicOcspBytes = extractBasicOcspResponseBytes(ocspHttpResponse);

                if (basicOcspBytes != null && basicOcspBytes.length > 0) {
                    return basicOcspBytes;
                }
            }

            return null;

        } catch (Exception e) {
            return null;
        }
    }

    private byte[] buildOcspRequestBytes(
            X509Certificate checkCert,
            X509Certificate issuerCert
    ) throws Exception {

        DigestCalculatorProvider digCalcProv =
                new JcaDigestCalculatorProviderBuilder()
                        .setProvider("BC")
                        .build();

        X509CertificateHolder issuerHolder =
                new JcaX509CertificateHolder(issuerCert);

        CertificateID certId = new CertificateID(
                digCalcProv.get(CertificateID.HASH_SHA1),
                issuerHolder,
                checkCert.getSerialNumber()
        );

        OCSPReqBuilder builder = new OCSPReqBuilder();
        builder.addRequest(certId);

        OCSPReq request = builder.build();
        return request.getEncoded();
    }

    private byte[] postOcspRequest(
            String ocspUrl,
            byte[] requestBytes
    ) {
        HttpURLConnection conn = null;

        try {
            URL url = new URL(ocspUrl);
            conn = (HttpURLConnection) url.openConnection();

            conn.setConnectTimeout(10000);
            conn.setReadTimeout(15000);
            conn.setInstanceFollowRedirects(false);
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/ocsp-request");
            conn.setRequestProperty("Accept", "application/ocsp-response");
            conn.setRequestProperty("User-Agent", "Java-Custom-OCSP/1.0");
            conn.setFixedLengthStreamingMode(requestBytes.length);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(requestBytes);
                os.flush();
            }

            if (conn.getResponseCode() != 200) {
                return null;
            }

            try (InputStream in = conn.getInputStream()) {
                return in.readAllBytes();
            }

        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /**
     * Para LtvVerification.addVerification(name, ocspList, crlList, certList),
     * iText espera BasicOCSPResp codificado, no el OCSPResp HTTP completo.
     */
    private byte[] extractBasicOcspResponseBytes(byte[] ocspResponseBytes) {
        try {
            OCSPResp ocspResp = new OCSPResp(ocspResponseBytes);

            if (ocspResp.getStatus() != OCSPResponseStatus.SUCCESSFUL) {
                return null;
            }

            Object responseObject = ocspResp.getResponseObject();

            if (!(responseObject instanceof BasicOCSPResp)) {
                return null;
            }

            BasicOCSPResp basicResp = (BasicOCSPResp) responseObject;

            return basicResp.getEncoded();

        } catch (Exception e) {
            return null;
        }
    }

    private List<String> getOcspUrls(X509Certificate cert) throws Exception {
        List<String> urls = new ArrayList<>();

        byte[] extVal = cert.getExtensionValue(Extension.authorityInfoAccess.getId());

        if (extVal == null) {
            return urls;
        }

        ASN1OctetString oct =
                (ASN1OctetString) ASN1Primitive.fromByteArray(extVal);

        AuthorityInformationAccess aia =
                AuthorityInformationAccess.getInstance(
                        ASN1Primitive.fromByteArray(oct.getOctets())
                );

        for (AccessDescription ad : aia.getAccessDescriptions()) {
            if (AccessDescription.id_ad_ocsp.equals(ad.getAccessMethod())) {
                GeneralName gn = ad.getAccessLocation();

                if (gn.getTagNo() == GeneralName.uniformResourceIdentifier) {
                    urls.add(gn.getName().toString());
                }
            }
        }

        return urls;
    }
}