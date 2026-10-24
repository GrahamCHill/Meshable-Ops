db = db.getSiblingDB('app_db');
db.createCollection('events');
db.events.createIndex({ userId: 1 });
db.events.createIndex({ createdAt: -1 });
