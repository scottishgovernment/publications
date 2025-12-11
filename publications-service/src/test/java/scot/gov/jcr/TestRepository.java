package scot.gov.jcr;


import org.apache.commons.io.IOUtils;
import org.apache.jackrabbit.api.JackrabbitNodeTypeManager;
import org.apache.jackrabbit.commons.JcrUtils;

import javax.jcr.*;
import javax.jcr.nodetype.NoSuchNodeTypeException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.*;

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
        Map<String, String> parameters = new HashMap<>();
        parameters.put("org.apache.jackrabbit.repository.home", repoDirectory().getPath());
        parameters.put("org.apache.jackrabbit.repository.conf", configFile().getPath());
        repository = JcrUtils.getRepository(parameters);
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
            return new File(targetDirectory(), "repo");
    }

    private static File configFile() {
        return new File(targetDirectory(), "test-classes/repository.xml");
    }

    private static File targetDirectory() {
        try {
            return new File(TestRepository.class
                    .getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI())
                    .getParentFile()
                    .getAbsoluteFile();
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
            "/hcm-config/hipposched.cnd",
            "/hcm-config/selectiontypes.cnd",
            "/hcm-config/govscot.cnd",
            "/hcm-config/embargo.cnd",
            "/hcm-config/searchjournal.cnd",
            "/hcm-config/sluglookup.cnd",
            "/hcm-config/bulkpublish.cnd"
            );


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