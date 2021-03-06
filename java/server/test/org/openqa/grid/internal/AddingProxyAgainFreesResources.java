// Licensed to the Software Freedom Conservancy (SFC) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The SFC licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

package org.openqa.grid.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openqa.grid.common.exception.GridException;
import org.openqa.grid.internal.mock.GridHelper;
import org.openqa.grid.web.servlet.handler.RequestHandler;
import org.openqa.selenium.remote.CapabilityType;
import org.openqa.selenium.support.ui.FluentWait;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * registering an already existing node assumes the node has been restarted, and all the resources
 * are free again
 *
 * @author freynaud
 */
public class AddingProxyAgainFreesResources {

  private Registry registry;

  private Map<String, Object> ff = new HashMap<>();
  private RemoteProxy p1;
  private RequestHandler handler;
  private RequestHandler handler2;
  private TestSession session = null;

  /**
   * create a hub with 1 node accepting 1 FF
   *
   * @throws InterruptedException
   */
  @Before
  public void setup() throws Exception {
    registry = Registry.newInstance();
    ff.put(CapabilityType.APPLICATION_NAME, "FF");
    p1 = RemoteProxyFactory.getNewBasicRemoteProxy(ff, "http://machine1:4444", registry);
    registry.add(p1);



    handler = GridHelper.createNewSessionHandler(registry, ff);

    // use all the spots ( so 1 ) of the grid so that a queue builds up
    handler.process();
    session = handler.getSession();

    // the test has been assigned.
    assertNotNull(session);

    // add the request to the queue

    handler2 = GridHelper.createNewSessionHandler(registry, ff);
    new Thread(() -> {handler2.process();}).start();
    // the 1 slot of the node is used.
    assertEquals(1, p1.getTotalUsed());

    // registering the node again should discard the existing test. The node
    // will be fresh as far as the grid is concerned so the 2nd test that
    // was waiting in the queue should be processed.
    registry.add(p1);

  }

  @Test(timeout = 1000)
  public void validateRequest2isNowRunningOnTheNode() throws InterruptedException {
    FluentWait<RequestHandler> wait = new FluentWait<>(handler2);
    wait.withTimeout(1, TimeUnit.SECONDS).pollingEvery(100, TimeUnit.MILLISECONDS)
      .ignoring(GridException.class)
      .until((RequestHandler input) -> input.getSession());
    assertNotNull(handler2.getSession());
  }

  @After
  public void teardown() {
    registry.stop();
  }
}
