db = db.getSiblingDB('app_db');
db.createCollection('events');
db.events.createIndex({ userId: 1 });
db.events.createIndex({ createdAt: -1 });
db.events.insertMany([
  { userId: 'seed', type: 'SYSTEM_START', payload: { message: 'MongoDB ready' }, createdAt: new Date() }
]);
print('MongoDB app_db initialised');
