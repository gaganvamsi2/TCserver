# TCserver

## Installation

Install docker.

Create the image from Dockerfile.
```bash
docker build -t <image-name>:<version> .
```
run the docker image by exposing the port 8080 to start the server.
```bash
docker run -p8080:8080 <image-name>:<version> 
```
