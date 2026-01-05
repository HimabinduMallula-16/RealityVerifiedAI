package ai.verifier;

import java.io.*;
import javax.servlet.*;
import javax.servlet.http.*;
import javax.servlet.annotation.WebServlet;
import java.net.HttpURLConnection;
import java.net.URL;


@WebServlet("/verify")
public class VerifyServlet extends HttpServlet {

    private static final String API_KEY = "AIzaSyAVzOz9pVVWz-KFx_5QFEXrAyoERkLttIM";

    
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("text/html");
        PrintWriter out = response.getWriter();
        out.println("<html><body>");
        out.println("<h2>Reality-Verified AI Servlet</h2>");
        out.println("<p>This servlet only accepts POST requests.</p>");
        out.println("<p><a href='index.html'>Go to the main page</a></p>");
        out.println("</body></html>");
    }
    
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        System.out.println("Request received");

        // Read request body
        BufferedReader reader = request.getReader();
        StringBuilder jsonBody = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            jsonBody.append(line);
        }

        String body = jsonBody.toString();
        System.out.println("Body: " + body);

        // Extract question safely
        String question = body.replace("{", "")
                               .replace("}", "")
                               .replace("\"", "")
                               .replace("question:", "")
                               .trim();

        // Call Gemini for reasoning
        String reasoning = callGemini(
            "Solve the following problem step by step. Number each step clearly.\n\nProblem:\n" + question
        );

        // Call Gemini for verification
        String verification = callGemini(
        	    "You are a strict verifier AI.\n"
        	  + "Below is the ORIGINAL problem statement and the AI-generated reasoning.\n"
        	  + "Verify whether the reasoning strictly follows the original problem facts.\n"
        	  + "Do NOT accept assumptions that contradict the original problem.\n"
        	  + "If any step violates the original facts, flag the exact step and explain why.\n"
        	  + "If everything is valid, say \"All steps are logically correct.\"\n\n"
        	  + "ORIGINAL PROBLEM:\n"
        	  + question + "\n\n"
        	  + "REASONING STEPS:\n"
        	  + reasoning
        	);


        // Send response
        response.setContentType("application/json");
        PrintWriter out = response.getWriter();
        out.print("{\"reasoning\":\"" + escape(reasoning) +
                  "\",\"verification\":\"" + escape(verification) + "\"}");
        out.flush();

        System.out.println("Response sent");
        listAvailableModels(); // Add this line temporarily to see available models
    }

    private String callGemini(String prompt) throws IOException {
        URL url = new URL(
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key="
            + API_KEY
        );
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("POST");
        con.setRequestProperty("Content-Type", "application/json");
        con.setDoOutput(true);
        
        String payload =
            "{ \"contents\": [ { \"role\": \"user\", \"parts\": [ { \"text\": \"" +
            escape(prompt) +
            "\" } ] } ] }";
        
        OutputStream os = con.getOutputStream();
        os.write(payload.getBytes("UTF-8"));
        os.close();
        
        InputStream is;
        if (con.getResponseCode() >= 200 && con.getResponseCode() < 300) {
            is = con.getInputStream();
        } else {
            is = con.getErrorStream();
        }
        
        BufferedReader br = new BufferedReader(new InputStreamReader(is));
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) {
            response.append(line);
        }
        
        System.out.println("Gemini raw response: " + response);
        String resp = response.toString();
        
        // NEW PARSING LOGIC - handles the nested structure
        try {
            // Find the text field inside candidates[0].content.parts[0].text
            int candidatesIdx = resp.indexOf("\"candidates\"");
            if (candidatesIdx == -1) {
                return "Could not find candidates in response.\nRaw:\n" + resp;
            }
            
            int textIdx = resp.indexOf("\"text\"", candidatesIdx);
            if (textIdx == -1) {
                return "Could not find text in response.\nRaw:\n" + resp;
            }
            
            int startQuote = resp.indexOf("\"", textIdx + 7);
            int endQuote = resp.indexOf("\"", startQuote + 1);
            
            // Handle escaped quotes in the text
            while (endQuote > 0 && resp.charAt(endQuote - 1) == '\\') {
                endQuote = resp.indexOf("\"", endQuote + 1);
            }
            
            if (startQuote == -1 || endQuote == -1) {
                return "Could not parse text field.\nRaw:\n" + resp;
            }
            
            String extractedText = resp.substring(startQuote + 1, endQuote);
            
            // Unescape the text
            return extractedText.replace("\\n", "\n")
                               .replace("\\\"", "\"")
                               .replace("\\\\", "\\");
            
        } catch (Exception e) {
            return "Error parsing response: " + e.getMessage() + "\nRaw:\n" + resp;
        }
    }

    private String escape(String text) {
        return text.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n");
    }
    private void listAvailableModels() throws IOException {
        URL url = new URL(
            "https://generativelanguage.googleapis.com/v1beta/models?key=" + API_KEY
        );
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("GET");
        
        BufferedReader br = new BufferedReader(new InputStreamReader(con.getInputStream()));
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) {
            response.append(line);
        }
        System.out.println("Available models: " + response);
    }
}

