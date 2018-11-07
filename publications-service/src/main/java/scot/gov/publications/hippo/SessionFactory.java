package scot.gov.publications.hippo;

import org.hippoecm.repository.HippoRepositoryFactory;
import scot.gov.publications.PublicationsConfiguration;

import javax.inject.Inject;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

public class SessionFactory {

    @Inject
    PublicationsConfiguration configuration;

    public Session newSession() throws RepositoryException {
        String url = configuration.getHippo().getUrl();
        String user = configuration.getHippo().getUser();
        String password = configuration.getHippo().getPassword();
        return HippoRepositoryFactory
                .getHippoRepository(url)
                .login(user, password.toCharArray());
    }
}
