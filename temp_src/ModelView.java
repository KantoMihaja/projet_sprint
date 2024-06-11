<<<<<<< HEAD
package mg.p16.Spring.models;
=======
package mg.p16.models;
>>>>>>> 04e72c6320d032860e719890998da4f67bb60673

import java.util.HashMap;

public class ModelView {
    private String url;
    private HashMap<String, Object> data;

    public ModelView(String url) {
        this.url = url;
        this.data = new HashMap<>();
    }

    public void addObject(String name, Object value) {
        data.put(name, value);
    }

    public String getUrl() {
        return url;
    }

    public HashMap<String, Object> getData() {
        return data;
    }
}
