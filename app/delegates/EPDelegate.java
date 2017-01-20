package delegates;

import net.gjerull.etherpad.client.EPLiteClient;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;


public class EPDelegate extends EPLiteClient {
    public EPDelegate(String url, String apiKey) {
        super(url, apiKey);
    }

    public EPDelegate(String url, String apiKey, String apiVersion) {
        super(url, apiKey, apiVersion);
    }

    /**
     * Create a new pad.
     *
     */
    public String createPad() {
        Map<String,Object> args = new HashMap<>();

        Map<String,Object> user = this.createAuthorIfNotExistsFor("1","Carmen");

        System.out.println("--------------------------AUTHOR"+ Arrays.toString(user.values().toArray()));
        Map<String,Object> group = this.createGroupIfNotExistsFor("1");
        System.out.println("--------------------------GROUP"+ Arrays.toString(group.values().toArray()));
        Map<String,Object> pad = this.createGroupPad(group.get("groupID").toString(),"newOsad222223");
        System.out.println("--------------------------PAD");
        return pad.get("padID").toString();
    }
}
