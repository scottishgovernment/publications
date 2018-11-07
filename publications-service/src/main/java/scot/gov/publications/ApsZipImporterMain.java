package scot.gov.publications;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.hippoecm.repository.HippoRepositoryFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scot.gov.publications.util.ZipEntryUtil;
import scot.mygov.config.Configuration;

import javax.jcr.RepositoryException;
import javax.jcr.Session;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static java.util.stream.Collectors.toList;

public final class ApsZipImporterMain {

    private static final Logger LOG = LoggerFactory.getLogger(ApsZipImporterMain.class);



    private ApsZipImporterMain() {
        // prevent instantiation
    }

//    public static void main(String[] args) throws Exception {
//        PublicationsConfiguration configuration
//                = Configuration.load(new PublicationsConfiguration(), APP_NAME).validate().getConfiguration();
//
//        LOG.info("Importing zip {}", args[0]);
//
//        Session session = getSession(configuration);
//        ApsZipImporter apsZipImporter = new ApsZipImporter(session, configuration);
//        ZipFile zipFile = new ZipFile(args[0]);
//        File extractedZipFile = getZipFileFromZip(zipFile);
//        try {
//            apsZipImporter.importApsZip(new ZipFile(extractedZipFile));
//        } finally {
//            FileUtils.deleteQuietly(extractedZipFile);
//        }
//        LOG.info("Done");
//    }
//
//    private static Session getSession(PublicationsConfiguration config) throws RepositoryException {
//        String url = config.getHippo().getUrl();
//        String user = config.getHippo().getUser();
//        String password = config.getHippo().getPassword();
//        return HippoRepositoryFactory
//                .getHippoRepository(url)
//                .login(user, password.toCharArray());
//    }
//    /**
//     * Extract nested ZIP file from within ZIP file.
//     * The caller should delete the returned temporary file.
//     */
//    private static File getZipFileFromZip(ZipFile zipFile) throws IOException {
//        List<ZipEntry> zipEntries = zipFile.stream()
//                .filter(ZipEntryUtil::isZip)
//                .filter(entry -> !entry.getName().startsWith("__MACOSX/"))
//                .collect(toList());
//        if (zipEntries.isEmpty()) {
//            throw new IllegalArgumentException("No zip in the zip!");
//        }
//        if (zipEntries.size() > 1) {
//            throw new IllegalArgumentException("More than one zip in the zip!");
//        }
//        File tmpDir = new File(System.getProperty("java.io.tmpdir"));
//        Path zipPath = Paths.get(zipFile.getName());
//        File extractedZip = new File(tmpDir, zipPath.getFileName().toString());
//        InputStream inputStream = zipFile.getInputStream(zipEntries.get(0));
//        IOUtils.copy(inputStream, new FileOutputStream(extractedZip));
//        return extractedZip;
//    }

}
