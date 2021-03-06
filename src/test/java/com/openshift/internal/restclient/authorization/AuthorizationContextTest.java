/*******************************************************************************
* Copyright (c) 2020 Red Hat, Inc. Distributed under license by Red Hat, Inc.
* All rights reserved. This program is made available under the terms of the
* Eclipse Public License v1.0 which accompanies this distribution, and is
* available at http://www.eclipse.org/legal/epl-v10.html
* 
* Contributors: Red Hat, Inc.
******************************************************************************/

package com.openshift.internal.restclient.authorization;


import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Field;
import java.time.Instant;

import org.jboss.dmr.ModelNode;
import org.junit.Before;
import org.junit.Test;

import com.openshift.internal.restclient.model.properties.ResourcePropertiesRegistry;
import com.openshift.internal.restclient.model.user.OpenShiftUser;
import com.openshift.restclient.IClient;
import com.openshift.restclient.OpenShiftException;
import com.openshift.restclient.ResourceKind;
import com.openshift.restclient.authorization.IAuthorizationContext;
import com.openshift.restclient.model.user.IUser;
import com.openshift.restclient.utils.Samples;

public class AuthorizationContextTest {

    private static final String VERSION = "v1";
    private static String TOKEN = "42";
    private static String EXPIRES = "84";
    private static String SCHEME = "Scheme";
    private AuthorizationContext context;
    private IClient client;
    
    @Before
    public void setup() throws Exception {
        IUser user = mock(IUser.class);
        this.context = new AuthorizationContext(TOKEN, EXPIRES, user, SCHEME); 
        this.client = createClient();
        context.setClient(client);
    }

    private IClient createClient() {
        IClient client = mock(IClient.class);
        ModelNode node = ModelNode.fromJSONString(Samples.V1_USER.getContentAsString());
        IUser user = new OpenShiftUser(node, client,
                ResourcePropertiesRegistry.getInstance().get(VERSION, ResourceKind.USER));
        doReturn(user).when(client).get(ResourceKind.USER, "~", "");
        return client;
    }

    @Test
    public void invalidateShouldForceNewUserRequestWhenEventualIsAuthorized() {
        // given
        assertThat(context.isAuthorized()).isTrue();
        context.invalidate();
        // when
        context.isAuthorized();
        // then
        verify(client, times(1)).get(ResourceKind.USER, "~", "");
    }

    @Test
    public void expiresShouldReturnInstantOfCreatePlusExpiresIn() throws Exception {
        // given
        Instant created = getCreated(context);
        String expiresIn = context.getExpiresIn();
        // when
        Instant expires = context.getExpires();
        // then
        assertThat(expires).isEqualTo(created.plusSeconds(Long.parseLong(expiresIn)));
    }

    @Test(expected = OpenShiftException.class)
    public void expiresShouldThrowIfExpiresInIsNotParseable() throws Exception {
        // given
        context.setExpiresIn("not a long");
        // when
        context.getExpires();
        // then
    }

    @Test
    public void expiresShouldReturnNullIfCreatedIsNull() throws Exception {
        // given
        setCreated(context, null);
        // when
        Instant expires = context.getExpires();
        // then
        assertThat(expires).isNull();
    }

    private Instant getCreated(IAuthorizationContext context) throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
        Field created = getCreatedField();
        return (Instant) created.get(context);
    }
    
    private void setCreated(IAuthorizationContext context, Instant created) throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
        Field createdField = getCreatedField();
        createdField.set(context, created);
    }

    private Field getCreatedField() throws NoSuchFieldException {
        Field createdField = AuthorizationContext.class.getDeclaredField("created");
        createdField.setAccessible(true);
        return createdField;
    }
}
