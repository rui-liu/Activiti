/* Licensed under the Apache License, Version 2.0 (the "License");
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

package org.activiti.rest.api.repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipInputStream;

import org.activiti.engine.ActivitiException;
import org.activiti.engine.ActivitiIllegalArgumentException;
import org.activiti.engine.impl.DeploymentQueryProperty;
import org.activiti.engine.query.QueryProperty;
import org.activiti.engine.repository.Deployment;
import org.activiti.engine.repository.DeploymentBuilder;
import org.activiti.engine.repository.DeploymentQuery;
import org.activiti.rest.api.ActivitiUtil;
import org.activiti.rest.api.DataResponse;
import org.activiti.rest.api.RestUrls;
import org.activiti.rest.api.SecuredResource;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.restlet.data.Status;
import org.restlet.ext.fileupload.RestletFileUpload;
import org.restlet.representation.Representation;
import org.restlet.resource.Get;
import org.restlet.resource.Post;

/**
 * @author Tijs Rademakers
 * @author Frederik Heremans
 */
public class DeploymentCollectionResource extends SecuredResource {
  
  protected static final String DEPRECATED_API_DEPLOYMENT_SEGMENT = "deployment";
  
  Map<String, QueryProperty> allowedSortProperties = new HashMap<String, QueryProperty>();
  
  public DeploymentCollectionResource() {
    allowedSortProperties.put("id", DeploymentQueryProperty.DEPLOYMENT_ID);
    allowedSortProperties.put("name", DeploymentQueryProperty.DEPLOYMENT_NAME);
    allowedSortProperties.put("deployTime", DeploymentQueryProperty.DEPLOY_TIME);
  }
  
  @Get
  public DataResponse getDeployments() {
    if(authenticate() == false) return null;
    
    DeploymentQuery deploymentQuery = ActivitiUtil.getRepositoryService().createDeploymentQuery();
    
    // Apply filters
    String name = getQuery().getFirstValue("deploymentName");
    String nameLike = getQuery().getFirstValue("deploymentNameLike");
    String category = getQuery().getFirstValue("deploymentCategory");
    String categoryNotEquals = getQuery().getFirstValue("deploymentCategoryNotEquals");
    
    if(name != null) {
      deploymentQuery.deploymentName(name);
    }
    if(nameLike != null) {
      deploymentQuery.deploymentNameLike(nameLike);
    }
    if(category != null) {
      deploymentQuery.deploymentCategory(category);
    }
    if(categoryNotEquals != null) {
      deploymentQuery.deploymentCategoryNotEquals(categoryNotEquals);
    }

    DataResponse response = new DeploymentsPaginateList(this).paginateList(getQuery(), 
        deploymentQuery, "id", allowedSortProperties);
    return response;
  }
  
  @Post
  public DeploymentResponse uploadDeployment(Representation entity) {
    try {
      if(authenticate() == false) return null;
      
      RestletFileUpload upload = new RestletFileUpload(new DiskFileItemFactory());
      List<FileItem> items = upload.parseRepresentation(entity);
      
      FileItem uploadItem = null;
      for (FileItem fileItem : items) {
        if(fileItem.getName() != null) {
          uploadItem = fileItem;
        }
      }
      
      DeploymentBuilder deploymentBuilder = ActivitiUtil.getRepositoryService().createDeployment();
      String fileName = uploadItem.getName();
      if (fileName.endsWith(".bpmn20.xml") || fileName.endsWith(".bpmn")) {
        deploymentBuilder.addInputStream(fileName, uploadItem.getInputStream());
      } else if (fileName.toLowerCase().endsWith(".bar") || fileName.toLowerCase().endsWith(".zip")) {
        deploymentBuilder.addZipInputStream(new ZipInputStream(uploadItem.getInputStream()));
      } else {
        throw new ActivitiIllegalArgumentException("File must be of type .bpmn20.xml, .bpmn, .bar or .zip");
      }
      deploymentBuilder.name(fileName);
      Deployment deployment = deploymentBuilder.deploy();
      
      setStatus(Status.SUCCESS_CREATED);
      
      DeploymentResponse response = new DeploymentResponse(deployment, 
              createFullResourceUrl(RestUrls.URL_DEPLOYMENT, deployment.getId()));
      return response;
      
    } catch (Exception e) {
      if(e instanceof ActivitiException) {
        throw (ActivitiException) e;
      }
      throw new ActivitiException(e.getMessage(), e);
    }
  }
}
