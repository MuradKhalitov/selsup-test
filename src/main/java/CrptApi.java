import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class CrptApi {
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Queue<Instant> requestTimestamps;
    private final Lock lock;
    private final Condition condition;
    private final TimeUnit timeUnit;
    private final int requestLimit;
    private final long timeIntervalMillis;

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        if (requestLimit <= 0) {
            throw new IllegalArgumentException("Request limit must be positive");
        }

        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
        this.requestTimestamps = new ConcurrentLinkedQueue<>();
        this.lock = new ReentrantLock();
        this.condition = lock.newCondition();
        this.timeUnit = timeUnit;
        this.requestLimit = requestLimit;
        this.timeIntervalMillis = timeUnit.toMillis(1);
    }

    public void createDocument(Document document, String signature) throws IOException, InterruptedException {
        String jsonDocument = objectMapper.writeValueAsString(document);
        submitRequest(jsonDocument, signature);
    }

    private void submitRequest(String jsonDocument, String signature) throws IOException, InterruptedException {
        lock.lock();
        try {
            while (requestTimestamps.size() >= requestLimit) {
                Instant oldestRequest = requestTimestamps.peek();
                if (oldestRequest != null && Instant.now().isBefore(oldestRequest.plusMillis(timeIntervalMillis))) {
                    condition.awaitNanos(TimeUnit.MILLISECONDS.toNanos(timeIntervalMillis));
                } else {
                    requestTimestamps.poll();
                }
            }
            requestTimestamps.add(Instant.now());
        } finally {
            lock.unlock();
        }

        String url = "https://ismp.crpt.ru/api/v3/lk/documents/create";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Signature", signature)
                .POST(HttpRequest.BodyPublishers.ofString(jsonDocument))
                .build();

        httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    public static class Document {
        public Description description;
        public String doc_id;
        public String doc_status;
        public String doc_type = "LP_INTRODUCE_GOODS";
        public boolean importRequest;
        public String owner_inn;
        public String participant_inn;
        public String producer_inn;
        public String production_date;
        public String production_type;
        public Product[] products;
        public String reg_date;
        public String reg_number;

        public static class Description {
            public String participantInn;
        }

        public static class Product {
            public String certificate_document;
            public String certificate_document_date;
            public String certificate_document_number;
            public String owner_inn;
            public String producer_inn;
            public String production_date;
            public String tnved_code;
            public String uit_code;
            public String uitu_code;
        }
    }

    public static void main(String[] args) throws IOException, InterruptedException {

        CrptApi api = new CrptApi(TimeUnit.MINUTES, 5);
        CrptApi.Document document = new CrptApi.Document();

        CrptApi.Document.Description description = new CrptApi.Document.Description();
        description.participantInn = "1234567890";
        document.description = description;

        document.doc_id = "unique-doc-id";
        document.doc_status = "DRAFT";
        document.importRequest = true;
        document.owner_inn = "1234567890";
        document.participant_inn = "1234567890";
        document.producer_inn = "0987654321";
        document.production_date = "2023-07-01";
        document.production_type = "PRODUCTION";

        CrptApi.Document.Product product = new CrptApi.Document.Product();
        product.certificate_document = "certificate-doc";
        product.certificate_document_date = "2023-06-30";
        product.certificate_document_number = "cert-1234";
        product.owner_inn = "1234567890";
        product.producer_inn = "0987654321";
        product.production_date = "2023-07-01";
        product.tnved_code = "1234567890";
        product.uit_code = "uit-1234";
        product.uitu_code = "uitu-5678";

        document.products = new CrptApi.Document.Product[]{product};
        document.reg_date = "2023-07-01";
        document.reg_number = "reg-1234";

        String signature = "example-signature";

        api.createDocument(document, signature);
    }
}

