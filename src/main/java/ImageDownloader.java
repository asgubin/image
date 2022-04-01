import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ImageDownloader {

    public ImageDownloader() {

        try (FileInputStream file = new FileInputStream("src/main/resources/config.properties")) {

            Properties properties = new Properties();
            properties.load(file);

            String srtURL = properties.getProperty("WEBSITE_URL");
            String folder = properties.getProperty("FOLDER_FOR_SAVE");
            String folderConcurrent = properties.getProperty("FOLDER_FOR_SAVE_CONCURRENT");
            long sizeImg = Long.parseLong(properties.getProperty("SIZE_IMG"));

            long startTime; // начала сохранения IMG
            long endTime;   // конец сохранения IMG

            // Получить все ссылки со стартовой страницы
            Set<String> linksSet = getLinks(srtURL);
            linksSet.add(srtURL);

            // сохраняем IMG 1 потоком
            startTime = System.currentTimeMillis();
            // Создать директории для сохранения IMG
            createFolder(folder);
            startSaveImage(linksSet, sizeImg, folder);
            endTime = System.currentTimeMillis();
            System.out.println("Времы сохранения IMG одним потоком: "
                    + (endTime - startTime) + " мс");

            // сохраняем IMG многопоточно
            startTime = System.currentTimeMillis();
            // Создать директории для сохранения IMG
            createFolder(folderConcurrent);
            startSaveImageConcurrent(linksSet, sizeImg, folderConcurrent);
            endTime = System.currentTimeMillis();
            System.out.println("Времы сохранения IMG многопоточно: "
                    + (endTime - startTime) + " мс");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void startSaveImage(Set<String> linksSet, long sizeImg, String folder) {

        for (String strURL : linksSet) {
            try {
                // Коннект к Web-сайту
                // document — объект документа представляет HTML DOM
                Document document = createDocument(strURL);

                // Получить все теги 'img'
                Elements imageElements = document.select("img");

                for (Element imageElement : imageElements) {

                    saveImage(imageElement.attr("abs:src"), sizeImg, folder);
                }

            } catch (IOException | IllegalArgumentException e)  {
                e.printStackTrace();
            }
        }

    }

    private Set<String> getLinks(String strURL) {

        Set<String> linksSet = new HashSet<>();

        try {
            // Коннект к Web-сайту
            // document — объект документа представляет HTML DOM
            Document document = createDocument(strURL);

            // Получить все теги 'a'
            Elements linkElements = document.select("a");

            // Собираем все ссылки
            for (Element linkElement : linkElements) {

                linksSet.add(linkElement.attr("abs:href"));
            }
        } catch (IOException | IllegalArgumentException e)  {
            e.printStackTrace();
        }

        return linksSet;
    }

    private void createFolder (String pathFolder) throws IOException {

        Path path = Paths.get(pathFolder);

        if(!Files.exists(path)) {

            Files.createDirectory(path);
        }
    }

    private Document createDocument (String strURL) throws IOException, IllegalArgumentException {

        return Jsoup
                .connect(strURL)
                .userAgent("Mozilla/5.0")
                .timeout(10 * 1000)
                .get();
    }

    private void saveImage(String imageURL, long sizeImg, String folder) {

        try {
            URL url = new URL(imageURL);

            try (InputStream in = url.openStream()) {
                // Получить имя изображения из пути

                String strImageName =
                        imageURL.substring(imageURL.lastIndexOf("/") + 1);

                Path path = Paths.get(folder + strImageName);

                // Сохранить изображение
                if (!Files.exists(path)) {

                    Files.copy(in, path);
                    //Размер файла килобайт
                    long size = Files.size(path) / 1024;

                    if (size < sizeImg) {
                        Files.delete(path);
                    }
                    else {
                        System.out.println(imageURL + " Size = " + size);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        } catch (MalformedURLException malformedURLException) {
            malformedURLException.printStackTrace();
        }
    }

    private void startSaveImageConcurrent(Set<String> linksSet, long sizeImg, String folder) {

        ExecutorService executor = Executors.newCachedThreadPool();

        for (String strURL : linksSet) {
            try {
                // Коннект к Web-сайту
                // document — объект документа представляет HTML DOM
                Document document = createDocument(strURL);

                // Получить все теги 'img'
                Elements imageElements = document.select("img");

                for (Element imageElement : imageElements) {

                    Runnable worker = saveImageConcurrent(
                            imageElement.attr("abs:src"), sizeImg, folder);
                    executor.execute(worker);
                }

            } catch (IOException | IllegalArgumentException e)  {
                e.printStackTrace();
            }
        }

        executor.shutdown();
    }

    private Runnable saveImageConcurrent (String imageURL, long sizeImg, String folder) {
        return () -> saveImage(imageURL, sizeImg, folder);
    }
}