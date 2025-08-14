db = db.getSiblingDB('user_management');

db.createUser({
   user: 'test_container',
   pwd: 'test_container',
   roles: [
      { role: 'readWrite', db: 'user_management' }
   ]
});
