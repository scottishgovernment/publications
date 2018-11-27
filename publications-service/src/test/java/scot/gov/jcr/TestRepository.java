package scot.gov.jcr;


import org.apache.commons.io.IOUtils;
import org.apache.jackrabbit.api.JackrabbitNodeTypeManager;
import org.apache.jackrabbit.commons.JcrUtils;

import javax.jcr.ImportUUIDBehavior;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Repository;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.SimpleCredentials;
import javax.jcr.Workspace;
import javax.jcr.nodetype.NoSuchNodeTypeException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Creates an embedded JCR repository for testing.
 *
 * The data is stored under target/repo.

 */
public class TestRepository {

    private static Repository repository;

    private static SimpleCredentials admin = new SimpleCredentials("admin", "admin".toCharArray());

    public static Session session() throws RepositoryException {
        Repository repo = repository();
        return repo.login(admin);
    }

    private static synchronized Repository repository() {
        if (repository != null) {
            return repository;
        }
        try {
            setUpRepo();
        } catch (RepositoryException | IOException ex) {
            throw new RuntimeException(ex);
        }
        return repository;
    }

    private static void setUpRepo() throws RepositoryException, IOException {
        String repoUrl = repoDirectory().toURI().toURL().toString();
        repository = JcrUtils.getRepository(repoUrl);
        Session session = repository.login(admin);
        Workspace workspace = session.getWorkspace();
        JackrabbitNodeTypeManager manager = (JackrabbitNodeTypeManager) workspace.getNodeTypeManager();

        // if hippo:resource is already defined then just return the repo
        try {
            manager.getNodeType("hippo:resource");
            return;
        } catch (NoSuchNodeTypeException ex) {
            // continue
        }

        // load the node types and bootstrap data
        loadCNDs(manager);
        importXML(session, "/", TestRepository.class.getResourceAsStream("/bootstrap.xml"));
        session.save();
        session.logout();
    }

    private static File repoDirectory() {
        try {
            File target = new File(TestRepository.class
                    .getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI())
                    .getParentFile();
            return new File(target, "repo");
        } catch (URISyntaxException ex) {
            throw new RuntimeException(ex);
        }
    }

    private static void loadCNDs(JackrabbitNodeTypeManager manager) throws IOException, RepositoryException {
        List<String> cnds = new ArrayList<>();
        Collections.addAll(cnds,
            "/hcm-config/hippo.cnd",
            "/hcm-config/hst-types.cnd",
            "/hcm-config/hippostd.cnd",
            "/hcm-config/hippostdpubwf.cnd",
            "/hcm-config/hippotranslations.cnd",
            "/hcm-config/hippogallery.cnd",
            "/hcm-config/gallerypickertypes.cnd",
            "/hcm-config/hippotaxonomy.cnd",
            "/hcm-config/resourcebundle.cnd",
            "/hcm-config/govscot.cnd");
        for (String cnd : cnds) {
            loadCND(manager, cnd);
        }
    }

    private static void loadCND(JackrabbitNodeTypeManager manager, String path) throws IOException, RepositoryException {
        InputStream is = TestRepository.class.getResourceAsStream(path);
        manager.registerNodeTypes(is, JackrabbitNodeTypeManager.TEXT_X_JCR_CND);
        IOUtils.closeQuietly(is);
    }

    private static void importXML(Session session, String path, InputStream is)
            throws RepositoryException, IOException {
        session.importXML(path, is, ImportUUIDBehavior.IMPORT_UUID_COLLISION_REPLACE_EXISTING);
    }

}