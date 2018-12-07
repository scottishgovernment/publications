package scot.gov.publications;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import org.junit.Test;
import scot.gov.publications.PublicationsConfiguration.S3;

import static org.assertj.core.api.Assertions.assertThat;

public class PublicationsModuleTest {

    @Test
    public void usesAWSCredentialsFromConfigurationIfAvailable() {
        String key = "AKIA0000000";
        String secret = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        S3 s3 = new S3();
        s3.setKey(key);
        s3.setSecret(secret);
        AWSCredentialsProvider provider = new PublicationsModule().configurationAWSCrendentials(s3);
        AWSCredentials credentials = provider.getCredentials();
        assertThat(credentials).isNotNull();
        assertThat(credentials.getAWSAccessKeyId()).isEqualTo(key);
        assertThat(credentials.getAWSSecretKey()).isEqualTo(secret);
    }

    @Test
    public void usesIAMRoleIfNoAWSCredentialsConfigured() {
        S3 s3 = new S3();
        AWSCredentialsProvider provider = new PublicationsModule().configurationAWSCrendentials(s3);
        AWSCredentials credentials = provider.getCredentials();
        assertThat(credentials).isNull();
    }

}
