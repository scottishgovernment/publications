package scot.gov.publications;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSCredentialsProviderChain;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.auth.EnvironmentVariableCredentialsProvider;
import com.amazonaws.regions.AwsRegionProvider;
import com.amazonaws.regions.AwsRegionProviderChain;
import com.amazonaws.regions.DefaultAwsRegionProviderChain;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.zaxxer.hikari.HikariDataSource;
import dagger.Module;
import dagger.Provides;
import org.apache.commons.dbutils.QueryRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scot.gov.publications.repo.TimestampSource;
import scot.gov.publications.rest.MaintenanceStatus;
import scot.gov.publications.storage.PublicationStorage;
import scot.gov.publications.storage.S3PublicationStorage;
import scot.mygov.config.Configuration;

import javax.inject.Singleton;
import javax.sql.DataSource;

@Module(injects = Publications.class)
class PublicationsModule {

    private static final Logger LOG = LoggerFactory.getLogger(PublicationsModule.class);

    private static String APP_NAME = "publications";

    @Provides
    @Singleton
    PublicationsConfiguration configuration() {
        Configuration<PublicationsConfiguration> configuration = Configuration
                .load(new PublicationsConfiguration(), APP_NAME)
                .validate();
        LOG.info("Configuration: {}", configuration);
        return configuration.getConfiguration();
    }

    @Provides
    @Singleton
    public DataSource dataSource(PublicationsConfiguration configuration) {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setJdbcUrl(configuration.getDatasource().getUrl());
        dataSource.setUsername(configuration.getDatasource().getUsername());
        dataSource.setPassword(configuration.getDatasource().getPassword());
        dataSource.setMaximumPoolSize(configuration.getDatasource().getMaxPoolSize());
        return dataSource;
    }

    @Provides
    @Singleton
    public QueryRunner queryRunner(DataSource dataSource) {
        return new QueryRunner(dataSource);
    }

    @Provides
    @Singleton
    public TimestampSource timestampSource() {
        return new TimestampSource();
    }

    @Provides
    @Singleton
    public PublicationStorage publicationStorage(S3PublicationStorage s3Storage) {
        return s3Storage;
    }

    @Provides
    @Singleton
    PublicationsConfiguration.S3 s3Configuration(PublicationsConfiguration configuration) {
        return configuration.getS3();
    }

    @Provides
    @Singleton
    AmazonS3 s3Client(AWSCredentialsProvider credentials, AwsRegionProvider region) {
        AWSCredentialsProvider credentialsProvider = new AWSCredentialsProviderChain(
                DefaultAWSCredentialsProviderChain.getInstance(),
                credentials
        );
        AwsRegionProvider regionProvider = new AwsRegionProviderChain(
                new DefaultAwsRegionProviderChain(),
                region
        );
        return AmazonS3ClientBuilder.standard()
                .withCredentials(credentialsProvider)
                .withRegion(regionProvider.getRegion())
                .build();
    }

    @Provides
    @Singleton
    AWSCredentialsProvider configurationAWSCrendentials(PublicationsConfiguration.S3 configuration) {
        return new EnvironmentVariableCredentialsProvider() {
            @Override
            public AWSCredentials getCredentials() {
                return new BasicAWSCredentials(configuration.getKey(), configuration.getSecret());
            }
        };
    }

    @Provides
    @Singleton
    AwsRegionProvider configurationAWSRegion(PublicationsConfiguration.S3 configuration) {
        return new AwsRegionProvider() {
            @Override
            public String getRegion() {
                return configuration.getRegion();
            }
        };
    }
    @Provides
    @Singleton
    MaintenanceStatus maintainanceStatus() {
        return new MaintenanceStatus();
    }

}
