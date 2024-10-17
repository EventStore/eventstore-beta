package main

import (
	"fmt"

	"github.com/EventStore/EventStore-Client-Go/v4/esdb"
)

func main() {

	esdb.NewClient()
	fmt.Println("hi")
}
