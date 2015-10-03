
import java.io.BufferedInputStream;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.Scanner;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import com.google.common.io.ByteStreams;

/**
 * LiveAgentInstance --- Provides a basic example of the use of the Live Agent REST API.
 * @author    Jose Garcia
 * 
 * 
 */

/* NOTE: Before testing this out, remember to modify the jsonfile.json and replace the placeholder text to use your org ID, 
 * Button ID and Deployment ID" * 
 */
public class LiveAgentInstance
 {
	private static final String ENDPOINT ="https://d.la2c1.salesforceliveagent.com";
	private static final String APIVERSION = "34";
	private static String affinityToken = null;
	private static String affinityKey = null;
	private static String sessionID = null;
	private static Integer sequence = 1;
	private static String lastmessage = null;
	private static String firstName = null;
	private static String lastName = null;
	private static String subject = null;
	private static String email = null;
	
	public static void main(String [] args) throws ParseException
	{	
		
		/* Use two threads for basic chat operation
		 * 1st Thread starts a session a takes input from the user which is sent to the server
		 * 2nd Thread polls the servers for new messages and session information
		 */
		Thread thread1 = new Thread () {
			
			  public void run () {
				  try {							
						startSession();
						//if we managed to get a session successfully then get the user details			
						if(sessionID != null){
							takeUserDetails();			
						}else{
							System.out.println("The session could not be started. Try again later...");
							System.exit(0);
						}
						
						//request a chat
						startChat();	
						
						Runnable helloRunnable2 = new Runnable() {
			    		    public void run() {
			    		    	try {
			    		    		//System.out.println(affinityToken);
			    		    		if (affinityToken != null)Chat();
								} catch (IOException e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
								}
			    		    }
			    		};
			    		
			    		ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);
			    		executor.scheduleAtFixedRate(helloRunnable2, 0, 1, TimeUnit.SECONDS);			    		
						
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
					} catch (ParseException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
			  }
		};
		/* This thread is used to retrieve the messages from the server*/
		Thread thread2 = new Thread () {
			  
			public void run () {
				  
			    		Runnable helloRunnable = new Runnable() {
			    		    public void run() {
			    		    	try {
			    		    		if (affinityToken != null)getMessages();
								} catch (IOException | ParseException e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
								}
			    		    }
			    		};
			    		ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);
			    		executor.scheduleAtFixedRate(helloRunnable, 0, 3, TimeUnit.SECONDS);	    		
					
			  }
			};
		
		thread1.start();
		thread2.start();		
	}
	
	private static void takeUserDetails() throws IOException{
		
		
		System.out.println("===============================");
	    System.out.println("|  Live Agent REST API DEMO   |");
	    System.out.println("===============================");
		Scanner in = new Scanner(System.in);
		System.out.print("Please enter your full name:"); 	
		String name = in.nextLine();
		String[] names = name.split(" ");
		firstName = names[0];
		lastName = names[names.length-1];
		System.out.print("Email Address :");
		email = in.nextLine();
		System.out.print("Subject :");
		subject = in.nextLine();
				
	}
	
	public static void Chat() throws ClientProtocolException, IOException{		
		
		Scanner in = new Scanner(System.in);		  
		lastmessage = in.nextLine();		
		sendMessage();
	}
	/*
	 * Establishes a new Live Agent session. The SessionId request is required as the first request to create every new Live Agent session.
	 */
	private static void startSession() throws IOException, ParseException{
		doGET("/chat/rest/System/SessionId",false);
	}	
	/*
	 * Initiates a new chat visitor session. The ChasitorInit request is always required as the first POST request in a new chat session.
	 */
	private static void startChat() throws IOException, ParseException {
		doPOST("/chat/rest/Chasitor/ChasitorInit");
	}
	
	private static void getMessages() throws IOException, ParseException{
		
		doGET("/chat/rest/System/Messages",true);
	}
	
	private static void sendMessage() throws ClientProtocolException, IOException {
		doPOST("/chat/rest/Chasitor/ChatMessage");
	}
	
	private static void doPOST (String path) throws ClientProtocolException, IOException{
				
		String fullPath = ENDPOINT + path;
		//System.out.println("Posting to " + fullPath);
		CloseableHttpClient httpclient = HttpClients.createDefault();		
		HttpPost httpPost = new HttpPost(fullPath);
		httpPost.addHeader("X-LIVEAGENT-API-VERSION", APIVERSION);
		httpPost.addHeader("X-LIVEAGENT-AFFINITY", affinityToken);
		httpPost.addHeader("X-LIVEAGENT-SESSION-KEY", affinityKey);
		httpPost.addHeader("X-LIVEAGENT-SEQUENCE", sequence.toString());
		
		if(path.equals("/chat/rest/Chasitor/ChatMessage") && lastmessage != null ){
			JSONObject visitorMsg = new JSONObject ();
			visitorMsg.put("text", lastmessage);			
			StringEntity params =new StringEntity(visitorMsg.toString());
			httpPost.setEntity(params);	
			lastmessage = null;
		}
		else{
			//load json file containing all prechat details
			StringEntity params =new StringEntity(readJSONFile());
			httpPost.setEntity(params);	
		}				
		CloseableHttpResponse response = httpclient.execute(httpPost);
		try {
		    //System.out.println(response.getStatusLine());
		    HttpEntity entity2 = response.getEntity();		   
		    byte[] buffer = new byte[1024];
		      if (entity2 != null) {
		        InputStream inputStream = entity2.getContent();
		        try {
		          int bytesRead = 0;
		          BufferedInputStream bis = new BufferedInputStream(inputStream);
		          while ((bytesRead = bis.read(buffer)) != -1) {
		            String chunk = new String(buffer, 0, bytesRead);
		            //System.out.println("Server Response " + chunk);	
		            
		           }
		        } catch (IOException ioException) {	        
		          ioException.printStackTrace();
		        }
		      }
		      sequence++;
		} finally {
		    response.close();
		}
	}	

	private static void doGET(String path, Boolean hasSession) throws IOException, ParseException {
		
		HttpClient httpClient = new DefaultHttpClient();
	    try {	    
	      String fullPath = ENDPOINT + path;	      
	      HttpGet httpGetRequest = new HttpGet(fullPath);
	      httpGetRequest.addHeader("X-LIVEAGENT-API-VERSION", APIVERSION);	      
	      if (hasSession){
	    	  httpGetRequest.addHeader("X-LIVEAGENT-SESSION-KEY", affinityKey);
	    	  httpGetRequest.addHeader("X-LIVEAGENT-AFFINITY", affinityToken);
	      }else{
	    	  httpGetRequest.addHeader("X-LIVEAGENT-AFFINITY", "null");
	      }
	     
	      HttpResponse httpResponse = httpClient.execute(httpGetRequest);	     
	      HttpEntity entity = httpResponse.getEntity(); 	      
	      byte[] buffer = new byte[1024];
	      if (entity != null) {
	        InputStream inputStream = entity.getContent();
	        try {
	          int bytesRead = 0;
	          BufferedInputStream bis = new BufferedInputStream(inputStream);
	          while ((bytesRead = bis.read(buffer)) != -1) {
	            String chunk = new String(buffer, 0, bytesRead); 
	            //System.out.println(chunk);		            
	            JSONObject json = (JSONObject)new JSONParser().parse(chunk);
	            
	            affinityToken = json.get("affinityToken") != null ? (String)json.get("affinityToken") : affinityToken;
	            affinityKey = json.get("key") != null ? (String)json.get("key") : affinityKey;
	            sessionID = json.get("id") != null ? (String)json.get("id") : sessionID;
	           
	    		if(path.equals("/chat/rest/System/Messages")){
	    			//iterate through JSON arrays
	    			JSONArray messages = (JSONArray) json.get("messages");
	    	        Iterator i = messages.iterator();
	    	        while (i.hasNext()) {
	    	            JSONObject msg = (JSONObject) i.next();
	    	            String msgtype = (String)msg.get("type");	    	            
	    	            if(msgtype.equals("ChatMessage")){	    	            	
	    	            	JSONObject chatmessage = (JSONObject)msg.get("message");
	    	            	System.out.println("\n" + chatmessage.get("name")+ " : " + chatmessage.get("text"));
	    	            }
	    	            else if(msgtype.equals("ChatEstablished")){
	    	            	JSONObject chatmessage = (JSONObject)msg.get("message");
	    	            	System.out.println("You have been connected to agent " + chatmessage.get("name")+ " \n");	  
	    	            }
	    	            else if(msgtype.equals("ChatEnded")){
	    	            	System.out.println("The chat session has ended");
	    	            	System.exit(0);
	    	            }
	    	            else if(msgtype.equals("ChatRequestSuccess")){
	    	            	System.out.println("The chat request has been successfully submitted!");
	    	            }
	    	            else if(msgtype.equals("ChatTransferred")){
	    	            	JSONObject chatmessage = (JSONObject)msg.get("message");
	    	            	System.out.println("The chat has been transferred to " + chatmessage.get("name"));
	    	            }
	    	            else if(msgtype.equals("ChatRequestFail")){
	    	            	
	    	            	System.out.println("The chat request has been terminated.");
	    	            	
	    	            	JSONObject chatmessage = (JSONObject)msg.get("message");
	    	            	
	    	            	if (chatmessage.get("reason").equals("Unavailable")){
	    	            		System.out.println("Reason: No agents available.");	    	            		
	    	            	}else{
	    	            		System.out.println("Reason: " + chatmessage.get("reason"));	    	            		
	    	            	}
	    	            	System.exit(0);	    	            	
	    	            }
	    	        }
	    		}	           
	          }
	        } catch (IOException ioException) {	        
	          ioException.printStackTrace();
	        } catch (RuntimeException runtimeException) {	         
	          httpGetRequest.abort();
	          runtimeException.printStackTrace();
	        } finally {	          
		          try {
		            inputStream.close();
		          } catch (Exception ignore) {
		          }
	        }
	      }
	    } catch (ClientProtocolException e) {	    
	      e.printStackTrace();
	    } catch (IOException e) {	     
	      e.printStackTrace();
	    } finally {	      
	      httpClient.getConnectionManager().shutdown();
	    }	   
	    
	}
	
	private static String readJSONFile() 
			  throws IOException 			{
			  InputStream in = ClassLoader.class.getResourceAsStream("/resources/jsonfile.json");			  
			  byte[] encoded = ByteStreams.toByteArray(in);
			  String jsonfile = new String(encoded, "UTF-8");
			  jsonfile = jsonfile.replace("MYSESSIONID", sessionID);
			  jsonfile = jsonfile.replace("VISITORNAME", firstName + " " + lastName).replace("MYLASTNAME", lastName).replace("MYFIRSTNAME",firstName);
			  jsonfile = jsonfile.replace("MYDEMOSUBJECT", subject);		
			  jsonfile = jsonfile.replace("MYEMAIL", email);			  
	  return jsonfile;			  
	}	
}