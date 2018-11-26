package scot.gov.publications;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3Client;
import com.zaxxer.hikari.HikariDataSource;
import dagger.Module;
import dagger.Provides;
import org.apache.commons.dbutils.QueryRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scot.gov.publications.repo.TimestampSource;
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
        LOG.info("{}", configuration);
        return configuration.getConfiguration();
    }

    @Provides
    @Singleton
    AmazonS3Client s3Client(PublicationsConfiguration configuration) {
        PublicationsConfiguration.S3 s3 = configuration.getS3();
        AWSCredentials credentials = new BasicAWSCredentials(s3.getKey(), s3.getSecret());
        return new AmazonS3Client(credentials);
    }

    @Provides
    @Singleton
    public DataSource dataSource(PublicationsConfiguration configuration) {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setJdbcUrl(configuration.getDatasource().getUrl());
        dataSource.setUsername(configuration.getDatasource().getUsername());
        dataSource.setPassword(configuration.getDatasource().getPassword());
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


}
