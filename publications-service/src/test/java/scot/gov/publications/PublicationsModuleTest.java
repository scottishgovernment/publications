package scot.gov.publications;

import org.junit.Test;
import scot.gov.publications.PublicationsConfiguration.S3;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkClientException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class PublicationsModuleTest {

    @Test
    public void usesAWSCredentialsFromConfigurationIfAvailable() {
        String key = "AKIA0000000";
        String secret = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        S3 s3 = new S3();
        s3.setKey(key);
        s3.setSecret(secret);
        AwsCredentialsProvider provider = new PublicationsModule().configurationAWSCrendentials(s3);
        AwsCredentials credentials = provider.resolveCredentials();
        assertThat(credentials).isNotNull();
        assertThat(credentials.accessKeyId()).isEqualTo(key);
        assertThat(credentials.secretAccessKey()).isEqualTo(secret);
    }

    @Test
    public void usesIAMRoleIfNoAWSCredentialsConfigured() {
        S3 s3 = new S3();
        AwsCredentialsProvider provider = new PublicationsModule().configurationAWSCrendentials(s3);
        assertThatThrownBy(provider::resolveCredentials)
                .isInstanceOf(SdkClientException.class);
    }

}
