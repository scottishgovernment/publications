package scot.gov.publications;

import dagger.Module;
import dagger.Provides;
import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.lang3.time.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scot.gov.publications.hippo.HippoSessionFactory;
import scot.gov.publications.hippo.SessionFactory;
import scot.gov.publications.imageprocessing.GraphicsMagickImageProcessingImpl;
import scot.gov.publications.imageprocessing.ImageProcessing;
import scot.gov.publications.storage.PublicationStorage;
import scot.gov.publications.storage.S3PublicationStorage;
import scot.gov.publications.util.Exif;
import scot.gov.publications.util.ExifProcessImpl;
import scot.mygov.config.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProviderChain;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.regions.providers.AwsRegionProvider;
import software.amazon.awssdk.regions.providers.AwsRegionProviderChain;
import software.amazon.awssdk.regions.providers.DefaultAwsRegionProviderChain;
import software.amazon.awssdk.services.s3.S3Client;

import javax.inject.Singleton;
import javax.sql.DataSource;
import java.time.Clock;

import com.zaxxer.hikari.HikariDataSource;

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
        LOG.info("Creating Hikari connection pool");
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
    SessionFactory sessionFactory(HippoSessionFactory sessionFactory) {
        return sessionFactory;
    }

    @Provides
    @Singleton
    ImageProcessing imageProcessing() {
        return new GraphicsMagickImageProcessingImpl();
    }

    @Provides
    @Singleton
    Exif exif() {
        return new ExifProcessImpl();
    }

    @Provides
    @Singleton
    public QueryRunner queryRunner(DataSource dataSource) {
        return new QueryRunner(dataSource);
    }

    @Provides
    @Singleton
    public Clock clock() {
        return Clock.systemDefaultZone();
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
    S3Client s3Client(AwsCredentialsProvider credentialsProvider, AwsRegionProvider regionProvider) {
        LOG.info("Creating Amazon S3 client");
        StopWatch watch = StopWatch.createStarted();

        AwsCredentialsProvider credentialsChain = AwsCredentialsProviderChain.builder()
                .credentialsProviders(credentialsProvider, DefaultCredentialsProvider.create())
                .build();

        AwsRegionProvider regionChain = new AwsRegionProviderChain(
                regionProvider,
                new DefaultAwsRegionProviderChain()
        );

        S3Client client = S3Client.builder()
                .credentialsProvider(credentialsChain)
                .region(regionChain.getRegion())
                .build();
        LOG.info("Created Amazon S3 client in {}ms", watch.getTime());
        return client;
    }

    @Provides
    @Singleton
    AwsCredentialsProvider configurationAWSCrendentials(PublicationsConfiguration.S3 configuration) {
        return () -> {
            if (configuration.getKey() == null || configuration.getSecret() == null) {
                throw SdkClientException.create("No AWS credentials in configuration");
            }
            LOG.info("Using AWS credentials from configuration");
            return AwsBasicCredentials.create(configuration.getKey(), configuration.getSecret());
        };
    }

    @Provides
    @Singleton
    AwsRegionProvider configurationAWSRegion(PublicationsConfiguration.S3 configuration) {
        return () -> {
            if (configuration.getRegion() == null) {
                throw SdkClientException.create("No AWS region in configuration");
            }
            return Region.of(configuration.getRegion());
        };
    }
}
