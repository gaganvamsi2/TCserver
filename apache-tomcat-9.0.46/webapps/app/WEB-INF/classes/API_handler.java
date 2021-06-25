
import java.io.*;
import org.apache.catalina.core.*;
import javax.servlet.*;
import javax.servlet.http.*;
import javax.servlet.annotation.*;
import java.lang.reflect.Field ;
import java.util.Base64;
import java.util.Properties;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;
import java.sql.*;
import java.util.regex.*; 
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;
import oracle.ucp.jdbc.PoolDataSourceFactory;
import oracle.ucp.jdbc.PoolDataSource;
import oracle.jdbc.pool.OracleDataSource;
import oracle.ucp.admin.*;
import oracle.ucp.jdbc.*;
import java.text.SimpleDateFormat;  
import java.util.Date;  


@WebServlet("/*")
public class API_handler extends HttpServlet {
    @Override
    public void init() throws ServletException {
        try {
            Field wrappedConfig = StandardWrapperFacade.class.getDeclaredField("config");
            wrappedConfig.setAccessible(true);
            StandardWrapper standardWrapper = (StandardWrapper) wrappedConfig.get(getServletConfig());
            standardWrapper.setMaxInstances(100);
        } catch (Exception e) {
            throw new ServletException("Failed to increment max instances", e);
        }
    }
    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        String DB_USER=null,DB_PASSWORD=null,database=null;
       
        String pathInfo = request.getPathInfo();
        boolean  issql = Pattern.matches("^.*/_/sql",pathInfo);
        boolean  isplsql = Pattern.matches("^.*/_/plsql",pathInfo);
        boolean  isjob = Pattern.matches("^.*/_/job",pathInfo);
        System.out.println(pathInfo);
        String[] pathParts = pathInfo.split("/");
        database = pathParts[1]; // {Schema alias}
        // check if the url is correct
        PrintWriter out = response.getWriter();
        try {
            if (!issql && !isplsql && !isjob) {
                response.setStatus(400);
                response.setContentType("application/json");
                response.setCharacterEncoding("UTF-8");
                try {
                    JSONObject obj = new JSONObject();
                    obj.put("error", "check the URL");
                    response.getWriter().print(obj);
                }
                catch(Exception exception) {
                    System.out.println("Error: " + exception.getMessage());
                }
            }
            else {
                // retrieve the auth details to connect to database
                final String authorization = request.getHeader("Authorization");
                //local-url
                // String DB_URL="jdbc:oracle:thin:@"+database.toLowerCase()+"_medium?TNS_ADMIN=C:\\Users\\GagaN\\Desktop\\Intern Project\\POC2\\TCserver\\apache-tomcat-9.0.46\\webapps\\app\\WEB-INF\\wallets\\"+database;
                //docker-url
                 String DB_URL="jdbc:oracle:thin:@"+database.toLowerCase()+"_medium?TNS_ADMIN=/usr/local/tomcat/webapps/ords/WEB-INF/wallets/"+database;

                if (authorization != null && authorization.toLowerCase().startsWith("basic")) {
                    // Authorization: Basic base64credentials
                    String base64Credentials = authorization.substring("Basic".length()).trim();
                    byte[] decodedBytes = Base64.getDecoder().decode(base64Credentials);
                    String decodedString = new String(decodedBytes);
                    // credentials = username:password
                    final String[] values = decodedString.split(":", 2);
                    DB_USER = values[0];
                    DB_PASSWORD = values[1];
                }
                System.out.println("going to connect to database");
                OracleDataSource ods = new OracleDataSource();
                ods.setURL(DB_URL);
                ods.setUser(DB_USER);
                ods.setPassword(DB_PASSWORD);
                Connection conn = ods.getConnection();
                System.out.println("Connection Successfull ............");
               
                //get the query from the body of the reque
                if(issql){
                    String query = null;
                    if ("POST".equalsIgnoreCase(request.getMethod())) {
                        query = request.getReader().lines().collect(Collectors.joining(System.lineSeparator()));
                    }
                    Statement stmt = conn.createStatement();
              
               // Step 3: Execute SQL  query
                    ResultSet rset = stmt.executeQuery(query);  // Send the query to the server
                    
                    JSONArray json = new JSONArray();
                    ResultSetMetaData rsmd = rset.getMetaData();
                    while (rset.next()) {
                        int numColumns = rsmd.getColumnCount();
                        JSONObject obj = new JSONObject();
                        for (int i = 1; i <= numColumns; i++) {
                            String column_name = rsmd.getColumnName(i);
                            obj.put(column_name, rset.getObject(column_name));
                        }
                        json.put(obj);
                    }
                    
                    response.setStatus(200);
                    response.setContentType("application/json");
                    response.setCharacterEncoding("UTF-8");
                    out.print(json);
                    out.flush();
                    conn.close();

                }
                
                else if(isplsql){

                   
                    String procedure = request.getHeader("procedure");
                    String call="{call "+procedure+"(";
                    int paramscount=Integer.parseInt(request.getHeader("paramscount"));
                    for(int i=0;i<paramscount-1;i++){
                        call=call+"?,";
                    }
                    if(paramscount>0){
                        call=call+"?)}";
                    }
                    else{
                        call=call+")}";
                    }
                   
                    CallableStatement cStmt = conn.prepareCall(call);
                   
                    for(int i=0;i<paramscount;i++){
                        String parameter = request.getHeader(String.valueOf(i+1));
                        String parametertype = request.getHeader(String.valueOf(i+1)+"_type");
                        
                        if("INT".equalsIgnoreCase(parametertype) || "number".equalsIgnoreCase(parametertype)){
                                cStmt.setInt(i+1, Integer.parseInt(parameter));
                               
                        }
                        else if("varchar".equalsIgnoreCase(parametertype)){
                            cStmt.setString(i+1, parameter);
                            
                        } 
                  
                        else if("date".equalsIgnoreCase(parametertype)){
                            java.util.Date javaDatetime = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss").parse(parameter);
                            Timestamp jdbcDatetime = new Timestamp(javaDatetime.getTime());
                            
                            cStmt.setTimestamp(i+1, jdbcDatetime);
                            
                        }
                        else if("bool".equalsIgnoreCase(parametertype) || "boolean".equalsIgnoreCase(parametertype) ){
                            cStmt.setBoolean(i+1, Boolean.parseBoolean(parameter));
                           if(Boolean.parseBoolean(parameter)){
                            cStmt.setInt(i+1, 1);
                           }
                           else{
                            cStmt.setInt(i+1, 0);
                           }
                        }
                        
                    }
                    boolean hadResults = cStmt.execute();
                    response.setStatus(200);
                    response.setContentType("application/json");
                    response.setCharacterEncoding("UTF-8");
                    JSONObject obj = new JSONObject();
                    obj.put("Success", "Executed the procedure "+procedure);
                    out.print(obj);
                    out.flush();
                    conn.close();
                }
                else if(isjob){
                    
                    String job_name     = request.getHeader("job_name");
                    String job_type     = request.getHeader("job_type");
                    String job_action   = request.getHeader("job_action");
                    String comments     = request.getHeader("comments");
                    String call="{call admin.sample_package.createjob(?,?,?,?)}";
                    CallableStatement cStmt = conn.prepareCall(call);
                    cStmt.setString(1, job_name);
                    cStmt.setString(2, job_type);
                    cStmt.setString(3, job_action);
                    cStmt.setString(4, comments);
                    cStmt.execute();
                    System.out.println("job successfully created");
                    response.setStatus(200);
                    response.setContentType("application/json");
                    response.setCharacterEncoding("UTF-8");
                    JSONObject obj = new JSONObject();
                    obj.put("Success", "Created the job with job_name "+job_name);
                    out.print(obj);
                    out.flush();
                    conn.close();

                }                
            }
        } catch(Exception ex) {
           // response.sendError(response.SC_BAD_REQUEST,ex.getMessage() );
            response.setStatus(400);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            try {
                JSONObject obj = new JSONObject();
                obj.put("error", ex.getMessage());
                response.getWriter().print(obj);
            }
            catch(Exception exception) {
                System.out.println("Error: " + exception.getMessage());
            }
        }
        System.out.println("Servlet is called");
        out.close();

    }

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        String DB_USER=null,DB_PASSWORD=null,database=null,job=null;
        String pathInfo = request.getPathInfo();
        boolean  isjob = Pattern.matches("^.*/_/job/.*",pathInfo);
        boolean  isrunning = Pattern.matches("^.*/_/job_running/.*",pathInfo);
        String[] pathParts = pathInfo.split("/");
        database = pathParts[1]; 
        job=pathParts[4];
        
        System.out.println(pathParts[3]);
        System.out.println(pathParts[4]);
        PrintWriter out = response.getWriter();
        
        try {
            if (!isjob && !isrunning) {   
                response.setStatus(400);
                response.setContentType("application/json");
                response.setCharacterEncoding("UTF-8");
                try {
                    JSONObject obj = new JSONObject();
                    obj.put("error", "check the URL");
                    response.getWriter().print(obj);
                }
                catch(Exception exception) {
                    System.out.println("Error: " + exception.getMessage());
                }
            }
            else {


                    // retrieve the auth details to connect to database
                    final String authorization = request.getHeader("Authorization");
                    //String DB_URL="jdbc:oracle:thin:@"+database.toLowerCase()+"_medium?TNS_ADMIN=C:\\Users\\GagaN\\Desktop\\Intern Project\\POC2\\TCserver\\apache-tomcat-9.0.46\\webapps\\app\\WEB-INF\\wallets\\"+database;
                    String DB_URL="jdbc:oracle:thin:@"+database.toLowerCase()+"_medium?TNS_ADMIN=/usr/local/tomcat/webapps/ords/WEB-INF/wallets/"+database;
                    if (authorization != null && authorization.toLowerCase().startsWith("basic")) {
                        // Authorization: Basic base64credentials
                        String base64Credentials = authorization.substring("Basic".length()).trim();
                        byte[] decodedBytes = Base64.getDecoder().decode(base64Credentials);
                        String decodedString = new String(decodedBytes);
                        // credentials = username:password
                        final String[] values = decodedString.split(":", 2);
                        DB_USER = values[0];
                        DB_PASSWORD = values[1];
                    }
                    System.out.println("going to connect to database");
                    OracleDataSource ods = new OracleDataSource();
                    ods.setURL(DB_URL);
                    ods.setUser(DB_USER);
                    ods.setPassword(DB_PASSWORD);
                    Connection conn = ods.getConnection();
                    System.out.println("Connection Successfull ............");

                    String query=null;
                    if(isjob){
                         query = "select * from dba_scheduler_job_run_details where job_name= '"+job.toUpperCase()+"'   ORDER by log_date DESC ";
                    }
                    else if(isrunning){
                         query = "select * from dba_scheduler_running_jobs where job_name= '"+job.toUpperCase()+"'";
                    }
                    System.out.println(query);
                    Statement stmt = conn.createStatement();
              
               // Step 3: Execute SQL  query
                    ResultSet rset = stmt.executeQuery(query);  // Send the query to the server
                    
                    JSONArray json = new JSONArray();
                    ResultSetMetaData rsmd = rset.getMetaData();
                    while (rset.next()) {
                        int numColumns = rsmd.getColumnCount();
                        JSONObject obj = new JSONObject();
                        for (int i = 1; i <= numColumns; i++) {
                            String column_name = rsmd.getColumnName(i);
                            obj.put(column_name, rset.getObject(column_name));
                        }
                        json.put(obj);
                    }
                    if(json.length()==0){
                        JSONObject obj = new JSONObject();
                        if(isrunning){
                            obj.put("error", "Job "+job+" is successfully completed or not created");
                        }else{
                            obj.put("error", "Job "+job+" is currently running or is not created");
                        }
                        out.print(obj);
                        
                    }else{
                        out.print(json);
                    }
                    
                    response.setStatus(200);
                    response.setContentType("application/json");
                    response.setCharacterEncoding("UTF-8");
                    
                    out.flush();
                    conn.close();


            
            }
        } catch(Exception ex) {
            // response.sendError(response.SC_BAD_REQUEST,ex.getMessage() );
             response.setStatus(400);
             response.setContentType("application/json");
             response.setCharacterEncoding("UTF-8");
             try {
                 JSONObject obj = new JSONObject();
                 obj.put("error", ex.getMessage());
                 response.getWriter().print(obj);
             }
             catch(Exception exception) {
                 System.out.println("Error: " + exception.getMessage());
             }
         }
         System.out.println("Servlet is called");
         out.close();


    }
}