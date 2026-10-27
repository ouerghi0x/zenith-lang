param([Parameter(ValueFromRemainingArguments = $true)]$args)
& java -jar "$PSScriptRoot/zenith.jar" @args
