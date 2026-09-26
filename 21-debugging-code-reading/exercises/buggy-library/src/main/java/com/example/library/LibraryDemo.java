package com.example.library;

import com.example.library.domain.Book;
import com.example.library.domain.Loan;
import com.example.library.domain.Member;
import com.example.library.domain.MemberType;
import com.example.library.policy.LoanPolicy;
import com.example.library.service.LibraryService;

import java.time.LocalDate;

/**
 * Tiny scripted walkthrough. Run with:
 *   mvn -q compile exec:java -Dexec.mainClass=com.example.library.LibraryDemo   (needs exec plugin)
 * or
 *   mvn -q package -DskipTests && java -jar target/buggy-library-0.3.0-SNAPSHOT.jar
 */
public final class LibraryDemo {

    private LibraryDemo() {
    }

    public static void main(String[] args) {
        LibraryService library = new LibraryService(LoanPolicy.DEFAULT);
        library.addBook(new Book("978-0134685991", "Effective Java", "Joshua Bloch", 2018));
        library.addBook(new Book("978-0596009205", "Head First Java", "Kathy Sierra", 2005));
        library.addBook(new Book("978-0132350884", "Clean Code", "Robert Martin", 2008));
        library.registerMember(new Member("M1", "Ada", "ada@example.com", MemberType.STANDARD));
        library.registerMember(new Member("M2", "Linus", null, MemberType.STUDENT));

        LocalDate day0 = LocalDate.of(2024, 3, 1);
        Loan loan = library.checkout("M2", "978-0134685991", day0);
        System.out.println("Checked out: " + loan);
        System.out.println("Search 'java': " + library.search("java"));

        LocalDate late = loan.getDueDate().plusDays(4);
        System.out.println("Overdue on " + late + ": " + library.overdueLoans(late));
        long fine = library.returnBook(loan.getId(), late);
        System.out.printf("Fine for %s: %d cents%n", loan.getId(), fine);
    }
}
