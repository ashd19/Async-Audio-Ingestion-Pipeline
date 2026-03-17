[1] Async audio ingestion pipeline <br/>
[2] hit the api /upload <br/>
User hits api ( uploads audio file ( mp3 for v1 testing ) ) ->
->validation and limit ( rate limiting ) ->
--> compression and other? <br/>

--> file is stored in object storage

[3] Springboot is multihreaded by nature  , it handles apis via thread pool of 200 workers thus if more then requests are queued