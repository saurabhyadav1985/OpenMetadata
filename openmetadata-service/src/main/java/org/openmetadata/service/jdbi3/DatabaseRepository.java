/*
 *  Copyright 2021 Collate
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *  http://www.apache.org/licenses/LICENSE-2.0
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.openmetadata.service.jdbi3;

import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.jdbi.v3.sqlobject.transaction.Transaction;
import org.openmetadata.schema.EntityInterface;
import org.openmetadata.schema.entity.data.Database;
import org.openmetadata.schema.entity.services.DatabaseService;
import org.openmetadata.schema.type.DatabaseProfilerConfig;
import org.openmetadata.schema.type.EntityReference;
import org.openmetadata.schema.type.Include;
import org.openmetadata.schema.type.Relationship;
import org.openmetadata.service.Entity;
import org.openmetadata.service.resources.databases.DatabaseResource;
import org.openmetadata.service.util.EntityUtil;
import org.openmetadata.service.util.EntityUtil.Fields;
import org.openmetadata.service.util.FullyQualifiedName;
import org.openmetadata.service.util.JsonUtils;

@Slf4j
public class DatabaseRepository extends EntityRepository<Database> {

  public static final String DATABASE_PROFILER_CONFIG_EXTENSION = "database.databaseProfilerConfig";

  public static final String DATABASE_PROFILER_CONFIG = "databaseProfilerConfig";

  public DatabaseRepository() {
    super(
        DatabaseResource.COLLECTION_PATH,
        Entity.DATABASE,
        Database.class,
        Entity.getCollectionDAO().databaseDAO(),
        "",
        "");
    supportsSearch = true;
  }

  @Override
  public void setFullyQualifiedName(Database database) {
    database.setFullyQualifiedName(
        FullyQualifiedName.build(database.getService().getName(), database.getName()));
  }

  @Override
  public void prepare(Database database, boolean update) {
    populateService(database);
  }

  @Override
  public void storeEntity(Database database, boolean update) {
    // Relationships and fields such as service are not stored as part of json
    EntityReference service = database.getService();
    database.withService(null);
    store(database, update);
    database.withService(service);
  }

  @Override
  public void storeRelationships(Database database) {
    addServiceRelationship(database, database.getService());
  }

  private List<EntityReference> getSchemas(Database database) {
    return database == null
        ? null
        : findTo(database.getId(), Entity.DATABASE, Relationship.CONTAINS, Entity.DATABASE_SCHEMA);
  }

  @Override
  public EntityInterface getParentEntity(Database entity, String fields) {
    return Entity.getEntity(entity.getService(), fields, Include.ALL);
  }

  public void setFields(Database database, Fields fields) {
    database.setService(getContainer(database.getId()));
    database.setSourceHash(fields.contains("sourceHash") ? database.getSourceHash() : null);
    database.setDatabaseSchemas(
        fields.contains("databaseSchemas") ? getSchemas(database) : database.getDatabaseSchemas());
    database.setDatabaseProfilerConfig(
        fields.contains(DATABASE_PROFILER_CONFIG)
            ? getDatabaseProfilerConfig(database)
            : database.getDatabaseProfilerConfig());
    if (database.getUsageSummary() == null) {
      database.setUsageSummary(
          fields.contains("usageSummary")
              ? EntityUtil.getLatestUsage(daoCollection.usageDAO(), database.getId())
              : null);
    }
  }

  public void clearFields(Database database, Fields fields) {
    database.setDatabaseSchemas(
        fields.contains("databaseSchemas") ? database.getDatabaseSchemas() : null);
    database.setDatabaseProfilerConfig(
        fields.contains(DATABASE_PROFILER_CONFIG) ? database.getDatabaseProfilerConfig() : null);
    database.withUsageSummary(fields.contains("usageSummary") ? database.getUsageSummary() : null);
  }

  @Override
  public void restorePatchAttributes(Database original, Database updated) {
    // Patch can't make changes to following fields. Ignore the changes
    super.restorePatchAttributes(original, updated);
    updated.withService(original.getService());
  }

  @Override
  public EntityRepository<Database>.EntityUpdater getUpdater(
      Database original, Database updated, Operation operation) {
    return new DatabaseUpdater(original, updated, operation);
  }

  private void populateService(Database database) {
    DatabaseService service = Entity.getEntity(database.getService(), "", Include.NON_DELETED);
    database.setService(service.getEntityReference());
    database.setServiceType(service.getServiceType());
  }

  public Database addDatabaseProfilerConfig(
      UUID databaseId, DatabaseProfilerConfig databaseProfilerConfig) {
    // Validate the request content
    Database database = find(databaseId, Include.NON_DELETED);
    if (databaseProfilerConfig.getProfileSampleType() != null
        && databaseProfilerConfig.getProfileSample() != null) {
      EntityUtil.validateProfileSample(
          databaseProfilerConfig.getProfileSampleType().toString(),
          databaseProfilerConfig.getProfileSample());
    }

    daoCollection
        .entityExtensionDAO()
        .insert(
            databaseId,
            DATABASE_PROFILER_CONFIG_EXTENSION,
            DATABASE_PROFILER_CONFIG,
            JsonUtils.pojoToJson(databaseProfilerConfig));
    clearFields(database, Fields.EMPTY_FIELDS);
    return database.withDatabaseProfilerConfig(databaseProfilerConfig);
  }

  public DatabaseProfilerConfig getDatabaseProfilerConfig(Database database) {
    return JsonUtils.readValue(
        daoCollection
            .entityExtensionDAO()
            .getExtension(database.getId(), DATABASE_PROFILER_CONFIG_EXTENSION),
        DatabaseProfilerConfig.class);
  }

  public Database deleteDatabaseProfilerConfig(UUID databaseId) {
    // Validate the request content
    Database database = find(databaseId, Include.NON_DELETED);
    daoCollection.entityExtensionDAO().delete(databaseId, DATABASE_PROFILER_CONFIG_EXTENSION);
    clearFieldsInternal(database, Fields.EMPTY_FIELDS);
    return database;
  }

  public class DatabaseUpdater extends EntityUpdater {
    public DatabaseUpdater(Database original, Database updated, Operation operation) {
      super(original, updated, operation);
    }

    @Transaction
    @Override
    public void entitySpecificUpdate() {
      recordChange("retentionPeriod", original.getRetentionPeriod(), updated.getRetentionPeriod());
      recordChange("sourceUrl", original.getSourceUrl(), updated.getSourceUrl());
      recordChange("sourceHash", original.getSourceHash(), updated.getSourceHash());
    }
  }
}
