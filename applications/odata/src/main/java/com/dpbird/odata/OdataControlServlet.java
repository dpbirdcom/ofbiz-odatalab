package com.dpbird.odata;

import org.apache.ofbiz.webapp.control.ControlServlet;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * ControlServlet.java - Master servlet for the web application.
 *
 * @date 2022-09-09
 */
public class OdataControlServlet extends ControlServlet {

    @Override
    public void doPut(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        doGet(request, response);
    }

    @Override
    public void doDelete(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        doGet(request, response);
    }

}
