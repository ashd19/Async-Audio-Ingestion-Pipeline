[1] Async audio ingestion pipeline <br/>
[2] hit the api /upload <br/>
User hits api ( uploads audio file ( mp3 for v1 testing ) ) ->
->validation and limit ( rate limiting ) ->
--> compression and other? <br/>

--> file is stored in object storage

[3] Springboot is multithreaded by nature  , it handles apis via thread pool of 200 workers thus if more then requests are queued 

[4] Using dto to serve requests since it prevents tight coupling and separates the entity from the request handling.
    essentially  Represents what the API accepts/returns , validation basically .
    Ex. Here we wouldn't want the user to send his ip address , we can extract it anyways.
