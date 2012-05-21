/* 
 * Copyright (c) 2012 Orderly Ltd. All rights reserved.
 *
 * This program is licensed to you under the Apache License Version 2.0,
 * and you may not use this file except in compliance with the Apache License Version 2.0.
 * You may obtain a copy of the Apache License Version 2.0 at http://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the Apache License Version 2.0 is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Apache License Version 2.0 for the specific language governing permissions and limitations there under.
 */
package com.snowplowanalytics.snowplow.hive.serde;

// Java
import java.text.SimpleDateFormat;
import java.text.ParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Hive
import org.apache.hadoop.hive.serde2.SerDeException;

import javax.servlet.jsp.PageContext;

/**
 * SnowPlowEventStruct represents the Hive struct for a SnowPlow event or page view.
 *
 * Contains a parse() method to perform an update-in-place for this instance
 * based on the current row's contents.
 *
 * Constructor is empty because we do updates-in-place for performance reasons.
 */
public class SnowPlowEventStruct {

  // -------------------------------------------------------------------------------------------------------------------
  // Mutable properties for this Hive struct
  // -------------------------------------------------------------------------------------------------------------------

  public String dt;
  public String tm;
  public String user_ipaddress;
  public String page_url;
  // TODO: add in rest of public fields

  // -------------------------------------------------------------------------------------------------------------------
  // Static configuration
  // -------------------------------------------------------------------------------------------------------------------

  // Define the regular expression for extracting the fields
  // Adapted from Amazon's own cloudfront-loganalyzer.tgz
  private static final String w = "[\\s]+"; // Whitespace regex
  private static final Pattern cfRegex = Pattern.compile("([\\S]+)"  // Date          / date
                                                   + w + "([\\S]+)"  // Time          / time
                                                   + w + "([\\S]+)"  // EdgeLocation  / x-edge-location
                                                   + w + "([\\S]+)"  // BytesSent     / sc-bytes
                                                   + w + "([\\S]+)"  // IPAddress     / c-ip
                                                   + w + "([\\S]+)"  // Operation     / cs-method
                                                   + w + "([\\S]+)"  // Domain        / cs(Host)
                                                   + w + "([\\S]+)"  // Object        / cs-uri-stem
                                                   + w + "([\\S]+)"  // HttpStatus    / sc-status
                                                   + w + "([\\S]+)"  // Referrer      / cs(Referer)
                                                   + w + "([\\S]+)"  // UserAgent     / cs(User Agent)
                                                   + w + "(.+)");    // Querystring   / cs-uri-query

  // -------------------------------------------------------------------------------------------------------------------
  // Deserialization logic
  // -------------------------------------------------------------------------------------------------------------------

  /**
   * Parses the input row String into a Java object.
   * For performance reasons this works in-place updating the fields
   * within this SnowPlowEventStruct, rather than creating a new one.
   * 
   * @param row The raw String containing the row contents
   * @return This struct with all values updated
   * @throws SerDeException For any exception during parsing
   */
  public Object parse(String row) throws SerDeException {
    
    // We have to handle any header rows
    if (row.startsWith("#Version:") || row.startsWith("#Fields:")) {
      return null; // Empty row will be discarded by Hive
    }

    final Matcher m = cfRegex.matcher(row);
    
    try {
      // Check our row is kosher
      m.matches();

      // 1. First we retrieve the fields which get directly passed through
      this.dt = m.group(1);
      this.tm = m.group(2); // No need for toHiveDate any more - CloudFront date format matches Hive's
      this.user_ipaddress = m.group(5);
      this.page_url = nullifyHyphen(m.group(10));

      // 2. Now we dis-assemble the user agent
      String useragent = m.group(11);
      // TODO

      // 3. Now we dis-assemble the querystring
      String querystring = m.group(12);
      if (querystring.compareTo("-") == 0) { // No querystring means this row wasn't generated by SnowPlow
        return null; // Skip the row
      } else {
        // TODO first check for hyphen and return null if no querystring
      }

    } catch (Exception e) {
      throw new SerDeException("Could not parse row: " + row, e);
    }

    return this; // Return the SnowPlowEventStruct
  }

  // -------------------------------------------------------------------------------------------------------------------
  // Datatype conversions
  // -------------------------------------------------------------------------------------------------------------------

  /**
   * Implicit conversion from String to Integer. To deal with
   * the fact that AWS uses a single "-"" for null.
   *
   * @param s The String to check
   * @return The Integer, or null if the String was "-" 
   */
  private Integer toInt(String s) { return (s.compareTo("-") == 0) ? null : Integer.valueOf(s); }

  /**
   * Explicit conversion to turn a "-" String into null.
   * Useful for "-" URIs (URI is set to "-" if e.g. S3 is accessed
   * from a file:// protocol).
   *
   * @param s The String to check
   * @return The original String, or null if the String was "-" 
   */
  private String nullifyHyphen(String s) { return (s.compareTo("-") == 0) ? null : s; }
}
