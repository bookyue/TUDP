# TUDP
Java based client/server for TCP-like reliable file transfer over UDP

uses Go-Back-N

Run the receiver side:
$ java Receiver <Port> <Filename>

A working example would be:

$ java Receiver 54321 receivedfile.jpg
Run the sender side:
$ java Sender <IPaddress> <Port> <Filename>

A working example would be:

$ java Sender localhost 54321 filetosend.jpg
