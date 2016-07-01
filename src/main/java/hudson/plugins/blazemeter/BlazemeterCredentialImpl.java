package hudson.plugins.blazemeter;

import com.cloudbees.plugins.credentials.BaseCredentials;
import com.cloudbees.plugins.credentials.CredentialsDescriptor;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import hudson.Extension;
import hudson.plugins.blazemeter.utils.Constants;
import hudson.plugins.blazemeter.utils.JobUtility;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONException;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import javax.mail.MessagingException;
import javax.servlet.ServletException;
import java.io.IOException;

/**
 * @author Vivek Pandey
 */
public class BlazemeterCredentialImpl extends BaseCredentials implements StandardCredentials {
    private static final long serialVersionUID = 1L;

    private String apiKey =null;
    private String description=null;

    @DataBoundConstructor
    public BlazemeterCredentialImpl(String apiKey,String description) {
        super(CredentialsScope.GLOBAL);
        this.apiKey=apiKey;
        this.description=description;
    }

    public String getId() {
        return StringUtils.left(apiKey,4) + Constants.CREDENTIALS_KEY + StringUtils.right(apiKey, 4);
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getDescription() {
        return description;
    }


    @Extension
    public static class DescriptorImpl extends CredentialsDescriptor {

        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            return Messages.BlazemeterCredential_DisplayName();
        }

        @Override
        public ListBoxModel doFillScopeItems() {
            ListBoxModel m = new ListBoxModel();
            m.add(CredentialsScope.GLOBAL.getDisplayName(), CredentialsScope.GLOBAL.toString());
            return m;
        }

        // Used by global.jelly to authenticate User key
        public FormValidation doTestConnection(@QueryParameter("apiKey") final String userKey) throws MessagingException, IOException, JSONException, ServletException {
            BlazeMeterPerformanceBuilderDescriptor descriptor=BlazeMeterPerformanceBuilderDescriptor.getDescriptor();
            return  JobUtility.validateUserKey(userKey,descriptor.getBlazeMeterURL());
        }

    }
}
