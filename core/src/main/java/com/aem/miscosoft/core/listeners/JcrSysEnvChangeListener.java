package com.aem.miscosoft.core.listeners;


import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.observation.Event;
import javax.jcr.observation.EventIterator;
import javax.jcr.observation.EventListener;
import javax.jcr.observation.ObservationManager;

import org.apache.commons.lang.StringUtils;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.apache.sling.engine.SlingSettingsService;
import org.apache.sling.jcr.api.SlingRepository;
import org.apache.sling.runmode.RunMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(immediate = true)
public class JcrSysEnvChangeListener {
    private static final Logger LOG = LoggerFactory.getLogger(JcrSysEnvChangeListener.class);

    private static final String ETC_NODE_PATH = "/etc";
    private static final String SYSTEM_ENV_NODE_PATH = "/etc/packages";

  
    @Reference
    private SlingRepository repository;
    
    
    @Reference
    RunMode runmode;

    private Session session = null;
    private ObservationManager observationManager = null;
    private EventListener sysEnvPropertiesListener = null;
    private EventListener etcNewChildrenListener = null;

    @Activate
    public void activate(@SuppressWarnings("rawtypes") final Map properties)
            throws Exception {
    	try {
       LOG.info("*********Service user : microsoft-team ******");
        session = repository.loginService("microsoft-team", null);
        LOG.info("User name:" +session.getUserID());

        observationManager = session.getWorkspace().getObservationManager();

        if (!session.nodeExists(SYSTEM_ENV_NODE_PATH)) {
            LOG.debug("Node at {} does not exist, listening for it to be created", SYSTEM_ENV_NODE_PATH);
            listenForNewChildrenAtEtc();
            return;
        }

        listenForChangesAtEtcSystemEnv();
        LOG.info("Listener for JCR path /etc/packages is active");
    	}catch(Exception e) {
    		LOG.error("Error: ",e);
    	}
    }
    
   protected String[] getRunModes() {
	   String[] runmodes = runmode.getCurrentRunModes();
	   return runmodes;
	}
   
   

    private void listenForChangesAtEtcSystemEnv() throws RepositoryException {
        sysEnvPropertiesListener = new SystemEnvPropertyChangedListener();
        observationManager.addEventListener(sysEnvPropertiesListener, Event.PROPERTY_ADDED | Event.PROPERTY_CHANGED | Event.PROPERTY_REMOVED,
                SYSTEM_ENV_NODE_PATH, true, null, null, true);
        LOG.debug("Listening for changes at {}", SYSTEM_ENV_NODE_PATH);
    }

    private void stopListeningForChangesAtEtcSystemEnv() {
        if (observationManager != null && sysEnvPropertiesListener != null) {
            try {
                observationManager.removeEventListener(sysEnvPropertiesListener);
                LOG.info("Stopped listening for changes at {}", SYSTEM_ENV_NODE_PATH);
            } catch (RepositoryException e) {
                LOG.error("Could not deregister event listener: " + e, e);
            }
        }
    }

    private void listenForNewChildrenAtEtc() throws RepositoryException {
        etcNewChildrenListener = new SystemEnvNodeAddedListener();
        observationManager.addEventListener(etcNewChildrenListener, Event.NODE_ADDED,
                ETC_NODE_PATH, true, null, null, true);
        LOG.debug("Listening for new nodes at {}", ETC_NODE_PATH);

        
    }

    private void stopListeningForForNewChildrenAtEtc() {
        if (observationManager != null && etcNewChildrenListener != null) {
            try {
                observationManager.removeEventListener(etcNewChildrenListener);
                LOG.info("Stopped listening for new nodes at {}", ETC_NODE_PATH);
            } catch (RepositoryException e) {
                LOG.error("Could not deregister event listener: " + e, e);
            }
        }
    }

    @Deactivate
    public void deactivate() {
        stopListeningForChangesAtEtcSystemEnv();
        stopListeningForForNewChildrenAtEtc();

        if (session != null) {
            session.logout();
            session = null;
        }
    }

    private final class SystemEnvPropertyChangedListener implements EventListener {
        @Override
        public void onEvent(EventIterator events) {
        	
        	//isAuthor();

            List<String> changedProperties = new ArrayList<String>();
            String userId = StringUtils.EMPTY;
            String packageName = StringUtils.EMPTY;
            String envName = StringUtils.EMPTY;
            
            String[] runmodes = getRunModes();
            if(Arrays.stream(runmodes).anyMatch("dev"::equals)) {
            	envName = "Development";
            }else if(Arrays.stream(runmodes).anyMatch("stage"::equals)) {
            	envName = "Staging";
            }else if(Arrays.stream(runmodes).anyMatch("prod"::equals)) {
            	envName = "Production";
            }else {
            	envName = "Local";
            }

            while (events.hasNext()) {
                Event event = events.nextEvent();

                try {
                	if(event.getPath().endsWith("/lastUnpacked")) {
                    changedProperties.add(event.getPath());
                    userId = event.getUserID();
                    String path = event.getPath();
                    if(path.contains(".zip")) {
            			String path1 = event.getPath().substring(0, path.lastIndexOf(".zip"));
            			packageName = StringUtils.substringAfterLast(path1, "/");
            		}
                    break;
                	}
                } catch (RepositoryException e) {
                    LOG.debug("Could not get path from event: " + e, e);
                }
            }

            if (!changedProperties.isEmpty()) {
                LOG.info("Reinstalling env-specific packages since the following properties have changed\n{}",
                        StringUtils.join(changedProperties, "\n"));

                AzureAD webhook = new AzureAD();
                String tenantId =   "*******"; 
                String clientId =  "******"; 
                String clientSecret =  "******" ; 
                try {
                clientSecret = java.net.URLEncoder.encode(clientSecret,"UTF-8");
                
					String accesstoken = webhook.getAccessToken(tenantId, clientId, clientSecret);
					LOG.debug("Accesstoken : {}",accesstoken);
					
					
					String result = webhook.postChangeToTeamChannel(accesstoken, envName, userId ,  packageName);
					LOG.debug("Result : {}",result);
					
				} catch (Exception e) {
					LOG.error("Error: ",e);
				}
                
            }

        }
    }

    private final class SystemEnvNodeAddedListener implements EventListener {
        @Override
        public void onEvent(EventIterator events) {

            while (events.hasNext()) {
                Event event = events.nextEvent();

                try {
                    if (StringUtils.equals(event.getPath(), SYSTEM_ENV_NODE_PATH)) {
                        LOG.info("Node {} was created, starting listener for it.", SYSTEM_ENV_NODE_PATH);
                        stopListeningForForNewChildrenAtEtc();
                        listenForChangesAtEtcSystemEnv();
                    }
                } catch (RepositoryException e) {
                    LOG.error("Could not get path from event: ", e);
                }

            }

        }
    }


}

