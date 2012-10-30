package com.enjin.officialplugin;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public class EnjinErrorReport {
	
	Throwable e = null;
	String otherinformation = "";
	long timethrown = System.currentTimeMillis();
	
	public EnjinErrorReport(Throwable e, String otherinformation) {
		this.e = e;
		this.otherinformation = otherinformation;
	}
	
	@Override
	public String toString() {
		StringBuilder errorstring = new StringBuilder();
		DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss z");
		Date date = new Date(timethrown);
		errorstring.append("Enjin plugin error report. Error generated on: " + dateFormat.format(date) + ":\n");
		errorstring.append("Extra data: " + otherinformation + "\n");
		errorstring.append("Stack trace:\n");
		errorstring.append(e.toString() + "\n");
		StackTraceElement[] stacktrace = e.getStackTrace();
		for(int i = 0; i < stacktrace.length; i++) {
			errorstring.append(stacktrace[i].toString() + "\n");
		}
		return errorstring.toString();
	}

}
