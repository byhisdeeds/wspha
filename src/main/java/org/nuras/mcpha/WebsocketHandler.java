/*
 * Copyright 2016 John Preston<byhisdeeds@gmail.com> NURAS.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.nuras.mcpha;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.*;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

/**
 *
 * @author John Preston<byhisdeeds@gmail.com>
 */
@WebSocket
public class WebsocketHandler
{
  private String sender, msg;

  @OnWebSocketConnect
  public void onConnect(Session user) throws Exception
  {
    String username = "User" + Client.nextUserNumber++;
    Client.userUsernameMap.put(user, username);
//    WSPHA.broadcastMessage(sender = "Server", msg = (username + " joined the chat"));
    Client.logDebugMessage("onConnect function - username="+username);
  }

  @OnWebSocketClose
  public void onClose(Session user, int statusCode, String reason)
  {
    String username = Client.userUsernameMap.get(user);
    
    Client.userUsernameMap.remove(user);
    
    Client.broadcastMessage(sender = "Server", msg = (username + " left the chat"));
    
    Client.logDebugMessage("onClose function - username="+username);
  }

  /**
   * 
   * @param user
   * @param message 
   */
  @OnWebSocketMessage
  public void onMessage(final Session user, String message)
  {
    String command = null;
    // parse json message
    JSONParser parser = new JSONParser();
    try
    {
      Client.logDebugMessage("MSG_RECEIVED -- sender="+sender+", message="+message);
      
      Client.sendJSONTextMessage(user.getRemote(), message);

      String msg;
      JSONObject json = (JSONObject)parser.parse(message);
      command = json.get("command").toString();
      if (command.equals("connect"))
      {
        String deviceip = json.get("deviceip").toString();
        long port = (long)json.get("port");
        Client.connectToDevice(user, deviceip, (int)port);
      }
      else if (command.equals("disconnect"))
      {
        Client.disconnectFromDevice(user);
      }
      else if (command.equals("set_sample_rate"))
      {
        long value = (long)json.get("value");
        Client.mcphaSetSampleRate(value);
      }
      else if (command.equals("set_roi"))
      {
        long roi = (long)json.get("roi");
        long start = (long)json.get("from");
        long end = (long)json.get("to");
        Client.mcphaSetRoi(user, (int)roi, (int)start, (int)end);
      }
      else if (command.equals("set_acquisition_state"))
      {
        switch ((int)(long)json.get("state"))
        {
          case 0: // STOP aquisition
            Client.mcphaStopAcquisition(user, 0);
            break;
          case 1: // START acquisition
            Client.mcphaSetTimerValue(0L, Client.TIMER_FREQ * (long)json.get("acqtime"));
            Client.mcphaResetTimer(0);
            Client.mcphaStartAcquisition(user, 0);
            break;
        }
      }
      else if (command.equals("get_acquisition_state"))
      {
        Client.mcphaGetAquisitionState(user);
      }
      else if (command.equals("clear_spectrum_data"))
      {
        Client.clearSpectrumData(user, 0L);
      }
      else if (command.equals("acq_oscilloscope"))
      {
        long channels = (long)json.get("channels");
        long trigger_level = (long)json.get("trigger_level");
        String trigger_slope  = json.get("trigger_slope").toString();
        String trigger_mode  = json.get("trigger_mode").toString();
        long trigger_source = (long)json.get("trigger_source");
        Client.acquireOscilloscopeData(user, (int)channels, trigger_mode,
          (int)trigger_level, trigger_slope, (int)trigger_source);
      }
    }
    catch (ParseException | IOException ex)
    {
      org.json.JSONObject j = new org.json.JSONObject();
      j.put("command", command);
      j.put("message", ex.toString());
      j.put("status", 1);
      try
      {
        Client.sendJSONObjectMessage(user.getRemote(), j);
      }
      catch (IOException ex1)
      {
        Logger.getLogger(WebsocketHandler.class.getName()).log(Level.SEVERE, null, ex1);
      }
    }
  }

}
