import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.PutItemOutcome;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.document.spec.GetItemSpec;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.GetItemResult;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SNSEvent;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailService;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailServiceClient;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailServiceClientBuilder;
import com.amazonaws.services.simpleemail.model.Body;
import com.amazonaws.services.simpleemail.model.Content;
import com.amazonaws.services.simpleemail.model.Message;
import com.amazonaws.services.simpleemail.model.SendEmailRequest;

import javax.print.attribute.standard.Destination;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class LogEvent implements RequestHandler<SNSEvent, Object> {

  public Object handleRequest(SNSEvent request, Context context) {

    String timeStamp = new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss").format(Calendar.getInstance().getTime());

    context.getLogger().log("Invocation started: " + timeStamp);

    context.getLogger().log("1: " + (request == null));

    context.getLogger().log("2: " + (request.getRecords().size()));

    context.getLogger().log(request.getRecords().get(0).getSNS().getMessage());

    timeStamp = new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss").format(Calendar.getInstance().getTime());

    context.getLogger().log("Invocation completed: " + timeStamp);

    String email=request.getRecords().get(0).getSNS().getMessage();

    AmazonDynamoDB amazonDynamoDB= AmazonDynamoDBClientBuilder.defaultClient();
    DynamoDB dynamoDB = new DynamoDB(amazonDynamoDB);
    String DBName=System.getenv("DBName");
    Table table=dynamoDB.getTable(DBName);


    //search
    /*Map<String,AttributeValue> key = new HashMap<>();
    key.put("Id",new AttributeValue().withN(email));
    GetItemResult outItem = amazonDynamoDB.getItem(DBName,key);
*/
    GetItemSpec getItemSpec;
    getItemSpec = new GetItemSpec()
            .withPrimaryKey("Id",email);
    Item outItem=table.getItem(getItemSpec);


    //judge
    if(outItem!=null) {
      long ttlTime = outItem.getLong("ttl");
      //Token still useful
      if (ttlTime > (System.currentTimeMillis() / 1000)){
        return "Password Reset Successfully";
      }
    }


    String id= UUID.randomUUID().toString();
    final String FROM = System.getenv("Sender");
    final String TO = email;
    final String CONFIGSET = "ConfigSet";
    final String SUBJECT = "Amazon SES test (AWS SDK for Java)";
    String HTMLBODY = "<h1>You can click the next url to reset your password</h1>"
            + "<a>"+"http://example.com/reset?email="+email+"&token="+id
            + "</a>";
    String TEXTBODY = "This email was sent through Amazon SES "
            + "using the AWS SDK for Java 1111111.";
    //add to DynamoDB
    long activeTime=System.currentTimeMillis()/1000;
    long ttltime=activeTime+20*60;

    PutItemOutcome putItemOutcome=table.putItem(
            new Item().withPrimaryKey("Id",email)
                    .withString("Token",id)
                    .withLong("ttl",ttltime));


    AmazonSimpleEmailService client = AmazonSimpleEmailServiceClientBuilder.defaultClient();
    SendEmailRequest sendEmailRequest = new SendEmailRequest()
            .withDestination(
                    new com.amazonaws.services.simpleemail.model.Destination()
                    .withToAddresses(TO))
            .withMessage(new Message()
                    .withBody(new Body()
                            .withHtml(new Content()
                                    .withCharset("UTF-8").withData(HTMLBODY))
                            .withText(new Content()
                                    .withCharset("UTF-8").withData(TEXTBODY)))
                    .withSubject(new Content()
                            .withCharset("UTF-8").withData(SUBJECT)))
            .withSource(FROM);
    client.sendEmail(sendEmailRequest);
    return "Password reset successfully";
  }

}

