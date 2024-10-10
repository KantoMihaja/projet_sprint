package mg.p16.Spring;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.*;

import com.google.gson.Gson;

import jakarta.servlet.http.HttpSession;

import jakarta.servlet.ServletConfig;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import mg.p16.annotations.AnnotationGetByURL;
import mg.p16.annotations.AnnotationPost;
import mg.p16.annotations.InjectSession;
import mg.p16.annotations.ParamObject;
import mg.p16.annotations.AnnotationController;
import mg.p16.annotations.Parametre;
import mg.p16.annotations.ParametreField;
import mg.p16.annotations.RequestParam;
import mg.p16.annotations.RestApi;
import mg.p16.models.CustomSession;
import mg.p16.models.ModelView;
import mg.p16.utile.Mapping;
import mg.p16.utile.VerbAction;
import mg.p16.annotations.Url;



public class FrontController extends HttpServlet {
    private String packageName; // Variable pour stocker le nom du package
    private static List<String> controllerNames = new ArrayList<>();
    private HashMap<String, Mapping> urlMaping = new HashMap<>();
    String error = "";

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        packageName = config.getInitParameter("packageControllerName"); // Recuperation du nom du package
        try {
            // Verification si le packageControllerName n'existe pas
            if (packageName == null || packageName.isEmpty()) {
                throw new Exception("Le nom du package du contrôleur n'est pas specifie.");
            }
            // Scanne les contrôleurs dans le package
            scanControllers(packageName);
        } catch (Exception e) {
            error = e.getMessage();
        }
    }

    protected void processRequest(HttpServletRequest request, HttpServletResponse response)
    throws Exception {
StringBuffer requestURL = request.getRequestURL();
String[] requestUrlSplitted = requestURL.toString().split("/");
String controllerSearched = requestUrlSplitted[requestUrlSplitted.length - 1];

PrintWriter out = response.getWriter();

response.setContentType("text/html");
if (!error.isEmpty()) {
    out.println(error);
} else if (!urlMaping.containsKey(controllerSearched)) {
    out.println("Aucune méthode associée au chemin spécifié.");
} else {
    try {
        Mapping mapping = urlMaping.get(controllerSearched);
        Class<?> clazz = Class.forName(mapping.getClassName());
        Object object = clazz.getDeclaredConstructor().newInstance();
        Method method = null;
        
        if (!mapping.isVerbPresent(request.getMethod())) {
            out.println("Le verbe HTTP utilisé n'est pas pris en charge pour cette action.");
        }

        for (Method m : clazz.getDeclaredMethods()) {
            for (VerbAction action : mapping.getVerbActions()) {
                if (m.getName().equals(action.getMethodeName()) && action.getVerb().equalsIgnoreCase(request.getMethod())) {
                    method = m;
                    break; 
                }
            }
            if (method != null) {
                break;
            }
            
        }
        if (method == null) {
            out.println("Aucune méthode correspondante trouvée");
        }

        // Inject parameters
        Object[] parameters = getMethodParameters(method, request);
        Object returnValue = method.invoke(object, parameters);
        if (method.isAnnotationPresent(RestApi.class)) {
            response.setContentType("application/json");
            Gson gson = new Gson();
            if (returnValue instanceof String) {
                String jsonResponse = gson.toJson(returnValue);
                out.println(jsonResponse);
            } else if (returnValue instanceof ModelView) {
                ModelView modelView = (ModelView) returnValue;
                String jsonResponse = gson.toJson(modelView.getData());
                out.println(jsonResponse);
            } else {
                out.println("Type de données non reconnu.");
            }
        }else{
            if (returnValue instanceof String) {
                out.println("Methode trouvee dans " + (String) returnValue);
            } else if (returnValue instanceof ModelView) {
                ModelView modelView = (ModelView) returnValue;
                for (Map.Entry<String, Object> entry : modelView.getData().entrySet()) {
                    request.setAttribute(entry.getKey(), entry.getValue());
                }
                RequestDispatcher dispatcher = request.getRequestDispatcher(modelView.getUrl());
                dispatcher.forward(request, response);
            } else {
                out.println("Type de données non reconnu.");
            }
        }
    } catch (Exception e) {
        out.println(e.getMessage());
    }
    
}
}

@Override
protected void doGet(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException {
    try {
        processRequest(request, response);
    } catch (Exception e) {
        e.printStackTrace();
        response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An internal error occurred");
    }
}

@Override
protected void doPost(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException {
    try {
        processRequest(request, response);
    } catch (Exception e) {
        e.printStackTrace();
        response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An internal error occurred");
    }
}
private void scanControllers(String packageName) throws Exception {
    try {
        // Charger le package et parcourir les classes
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        String path = packageName.replace('.', '/');
        URL resource = classLoader.getResource(path);

        // Verification si le package n'existe pas
        if (resource == null) {
            throw new Exception("Le package specifie n'existe pas: " + packageName);
        }

        Path classPath = Paths.get(resource.toURI());
        Files.walk(classPath)
                .filter(f -> f.toString().endsWith(".class"))
                .forEach(f -> {
                    String className = packageName + "." + f.getFileName().toString().replace(".class", "");
                    try {
                        Class<?> clazz = Class.forName(className);
                        if (clazz.isAnnotationPresent(AnnotationController.class)
                                && !Modifier.isAbstract(clazz.getModifiers())) {
                            controllerNames.add(clazz.getSimpleName());
                            Method[] methods = clazz.getMethods();
                            for (Method method : methods) {
                                if (method.isAnnotationPresent(Url.class)) {
                                    Url urlAnnotation = method.getAnnotation(Url.class);
                                    String url = urlAnnotation.value();
                                    String verb = "GET"; 
                                    if (method.isAnnotationPresent(AnnotationGetByURL.class)) {
                                        verb = "GET";
                                    } else if (method.isAnnotationPresent(AnnotationPost.class)) {
                                        verb = "POST";
                                    }
                                    VerbAction verbAction = new VerbAction(method.getName(), verb);
                                    Mapping map = new Mapping(className);
                                    if (urlMaping.containsKey(url)) {
                                        Mapping existingMap = urlMaping.get(url);
                                        if (existingMap.getVerbActions().contains(verbAction)) {
                                            throw new Exception("Duplicate URL: " + url);
                                        } else {
                                            existingMap.setVerbActions(verbAction);
                                        }
                                    } else {
                                        map.setVerbActions(verbAction);
                                        urlMaping.put(url, map);
                                    }
                                    
                                }else{
                                    throw new Exception("il faut avoir une annotation url dans le controlleur  "+ className);
                                }
                            }
                            
                            
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
    } catch (Exception e) {
        throw e;
    }
}

public static Object convertParameter(String value, Class<?> type) {
        if (value == null) {
            return null;
        }
        if (type == String.class) {
            return value;
        } else if (type == int.class || type == Integer.class) {
            return Integer.parseInt(value);
        } else if (type == long.class || type == Long.class) {
            return Long.parseLong(value);
        } else if (type == boolean.class || type == Boolean.class) {
            return Boolean.parseBoolean(value);
        }
        // Ajoutez d'autres conversions nécessaires ici
        return null;
    }

    

    private Object[] getMethodParameters(Method method, HttpServletRequest request)throws Exception {
        Parameter[] parameters = method.getParameters();
        Object[] parameterValues = new Object[parameters.length];

        for (int i = 0; i < parameters.length; i++) {
            
            if (parameters[i].isAnnotationPresent(Parametre.class)) {
                Parametre param = parameters[i].getAnnotation(Parametre.class);
                String paramValue = request.getParameter(param.value());
                parameterValues[i] = convertParameter(paramValue, parameters[i].getType()); // Assuming all parameters are strings for simplicity
            }
            // Vérifie si le paramètre est annoté avec @RequestObject
            else if (parameters[i].isAnnotationPresent(ParamObject.class)) {
                Class<?> parameterType = parameters[i].getType();  // Récupère le type du paramètre (le type de l'objet à créer)
                Object parameterObject = parameterType.getDeclaredConstructor().newInstance();  // Crée une nouvelle instance de cet objet
    
                // Parcourt tous les champs (fields) de l'objet
                for (Field field : parameterType.getDeclaredFields()) {
                    ParametreField param = field.getAnnotation(ParametreField.class);
                    String fieldName = field.getName();  // Récupère le nom du champ
                    if (param == null) {
                        throw new Exception("Etu002418 ,l'attribut " + fieldName +" dans le classe "+parameterObject.getClass().getSimpleName()+" n'a pas d'annotation ParamField "); 
                    }  
                    String paramName = param.value();
                    String paramValue = request.getParameter(paramName);  // Récupère la valeur du paramètre de la requête

                    // Vérifie si la valeur du paramètre n'est pas null (si elle est trouvée dans la requête)
                    if (paramValue != null) {
                        Object convertedValue = convertParameter(paramValue, field.getType());  // Convertit la valeur de la requête en type de champ requis

                        // Construit le nom du setter
                        String setterName = "set" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
                        Method setter = parameterType.getMethod(setterName, field.getType());  // Récupère la méthode setter correspondante
                        setter.invoke(parameterObject, convertedValue);  // Appelle le setter pour définir la valeur convertie dans le champ de l'objet
                    }
                }
                parameterValues[i] = parameterObject;  // Stocke l'objet créé dans le tableau des arguments
            }else if (parameters[i].isAnnotationPresent(InjectSession.class)) {
                parameterValues[i] = new CustomSession(request.getSession());
            }
            else{

            }
        }

        return parameterValues;
    }
    private void injectSessionIfNeeded(Object controllerInstance, HttpSession session) throws IllegalAccessException {
        Field[] fields = controllerInstance.getClass().getDeclaredFields();
        for (Field field : fields) {
            if (field.isAnnotationPresent(InjectSession.class)) {
                boolean accessible = field.isAccessible();
                field.setAccessible(true);
                field.set(controllerInstance, new CustomSession(session));
                field.setAccessible(accessible);
            }
        }
    }
}
