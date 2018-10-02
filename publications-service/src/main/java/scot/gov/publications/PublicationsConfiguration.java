package scot.gov.publications;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;

public class PublicationsConfiguration {

    @Valid
    Hippo hippo = new Hippo();

    public Hippo getHippo() {
        return hippo;
    }

    public void setHippo(Hippo hippo) {
        this.hippo = hippo;
    }

    public static class Hippo {

        @Pattern(regexp = "rmi://.*")
        private String url = "rmi://localhost:1099/hipporepository";

        @NotNull
        private String user;

        @NotNull
        private String password;

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getUser() {
            return user;
        }

        public void setUser(String user) {
            this.user = user;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }
}
