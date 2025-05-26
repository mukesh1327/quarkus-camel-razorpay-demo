package org.noventiqvp;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/")
public class Welcome {
    // private static final String IMAGE_URL = "/images/noventiq-vp.png";

    @ConfigProperty(name = "app.title")
    String appTitle;

    @ConfigProperty(name = "app.image")
    String welcomeImg;

    // @GET
    // @Produces(MediaType.TEXT_PLAIN)
    // public String helloWorld() {
    //     return "Hello, World! Check with endpoint /noventiq-vp-hello";
    // }

    public String getImageUrl() {
        return welcomeImg;
    }

    @GET
    @Path("/")
    @Produces(MediaType.TEXT_HTML)
    public String getImageHtml() {
        return "<html>" +
                "<head><title>Image Viewer</title></head>" +
                "<body>" +
                "<h1>" + appTitle + "</h1>" +
                "<h2>App modernization</h2>" +
                "<img src='" + welcomeImg + "' alt='Image' width='500'/>" +
                "</body>" +
                "</html>";
    }
}