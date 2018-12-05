package scot.gov.publications.hippo;

import org.hippoecm.repository.HippoRepositoryFactory;
import scot.gov.publications.PublicationsConfiguration;

import javax.inject.Inject;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

/**
 * Factory for JCR sessions
 */
public class SessionFactory {

    @Inject
    PublicationsConfiguration configuration;

    /**
     * Create a new JCR session.
     *
     * @return new JCR session
     * @throws RepositoryException if session could not be created
     */
    public Session newSession() throws RepositoryException {
        String url = configuration.getHippo().getUrl();
        String user = configuration.getHippo().getUser();
        String password = configuration.getHippo().getPassword();
        return HippoRepositoryFactory
                .getHippoRepository(url)
                .login(user, password.toCharArray());
    }
}
